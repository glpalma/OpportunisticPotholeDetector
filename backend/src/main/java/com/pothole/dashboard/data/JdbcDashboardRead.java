package com.pothole.dashboard.data;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.pothole.dashboard.domain.ClientStatus;
import com.pothole.dashboard.domain.DashboardRead;
import com.pothole.dashboard.domain.DashboardSighting;
import com.pothole.dashboard.domain.DashboardStats;
import com.pothole.dashboard.domain.FleetStatus;
import com.pothole.dashboard.domain.InvalidDashboardQueryException;
import com.pothole.dashboard.domain.MapPoint;
import com.pothole.dashboard.domain.PotholeCriteria;
import com.pothole.dashboard.domain.PotholeDetail;
import com.pothole.dashboard.domain.PotholePage;
import com.pothole.dashboard.domain.PotholeSummary;
import com.pothole.dashboard.domain.ReviewCandidate;

import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcDashboardRead implements DashboardRead {

    private static final Duration LAST_SYNC_WINDOW = Duration.ofMinutes(15);

    private static final String FROM = """
            FROM potholes p
            LEFT JOIN (
                SELECT pothole_id,
                       COUNT(*) FILTER (WHERE review IS NOT NULL)::int AS reviewed_count,
                       COUNT(*) FILTER (WHERE review = 'false_positive')::int AS false_positive_count,
                       COUNT(*) FILTER (WHERE review IS NULL)::int AS unreviewed_count
                FROM sightings
                GROUP BY pothole_id
            ) agg ON agg.pothole_id = p.id
            """;

    private static final String SUMMARY = """
            SELECT p.id, p.lat, p.lon, p.status, p.first_seen_at, p.last_seen_at,
                   p.sighting_count, p.max_confidence,
                   COALESCE(agg.reviewed_count, 0) AS reviewed_count,
                   COALESCE(agg.false_positive_count, 0) AS false_positive_count,
                   (SELECT s.sighting_id FROM sightings s
                     WHERE s.pothole_id = p.id
                     ORDER BY s.confidence DESC, s.captured_at DESC
                     LIMIT 1) AS thumbnail_sighting_id
            """;

    private static final Map<String, String> SORTS = Map.of(
            "lastSeenAt", "p.last_seen_at",
            "firstSeenAt", "p.first_seen_at",
            "sightingCount", "p.sighting_count",
            "maxConfidence", "p.max_confidence",
            "status", "p.status",
            "reviewedCount", "reviewed_count");

    private final JdbcClient jdbc;

    JdbcDashboardRead(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<MapPoint> map(PotholeCriteria criteria) {
        if (!criteria.bounded()) {
            throw new InvalidDashboardQueryException("bounds are required");
        }
        Sql where = where(criteria, true);
        String sql = """
                SELECT p.id, p.lat, p.lon, p.status, p.first_seen_at
                """ + FROM + where.sql() + """
                 ORDER BY p.id
                 LIMIT 
                """ + MAP_LIMIT;
        return statement(sql, where.params())
                .query((rs, row) -> new MapPoint(
                        rs.getObject("id", UUID.class),
                        rs.getDouble("lat"),
                        rs.getDouble("lon"),
                        rs.getString("status"),
                        neglected(rs.getString("status"), instant(rs, "first_seen_at"), criteria.neglectCutoff())))
                .list();
    }

    @Override
    public PotholePage list(PotholeCriteria criteria) {
        Sql where = where(criteria, false);
        long total = statement("SELECT COUNT(*) AS n " + FROM + where.sql(), where.params())
                .query((rs, row) -> rs.getLong("n"))
                .single();
        Map<String, Object> params = new LinkedHashMap<>(where.params());
        params.put("limit", criteria.limit());
        params.put("offset", criteria.offset());
        String sql = SUMMARY + FROM + where.sql()
                + " ORDER BY " + sortColumn(criteria.sort()) + (criteria.ascending() ? " ASC" : " DESC")
                + ", p.id ASC LIMIT :limit OFFSET :offset";
        List<PotholeSummary> items = statement(sql, params)
                .query((rs, row) -> summary(rs, criteria.neglectCutoff()))
                .list();
        int pageSize = criteria.limit();
        int page = pageSize == 0 ? 1 : (int) (criteria.offset() / pageSize) + 1;
        return new PotholePage(page, pageSize, total, items);
    }

    @Override
    public Optional<PotholeDetail> detail(UUID id, Instant neglectCutoff) {
        List<PotholeSummary> rows = statement(SUMMARY + FROM + " WHERE p.id = :id", Map.of("id", id))
                .query((rs, row) -> summary(rs, neglectCutoff))
                .list();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        PotholeSummary summary = rows.get(0);
        List<DashboardSighting> sightings = statement("""
                SELECT sighting_id, client_id, captured_at, received_at, lat, lon, heading, speed,
                       horizontal_accuracy_m, confidence, bbox_x, bbox_y, bbox_width, bbox_height,
                       review, reviewed_at, image_path
                FROM sightings
                WHERE pothole_id = :id
                ORDER BY captured_at, sighting_id
                """, Map.of("id", id))
                .query((rs, row) -> sighting(rs))
                .list();
        return Optional.of(new PotholeDetail(
                summary.id(),
                summary.lat(),
                summary.lon(),
                summary.status(),
                summary.neglected(),
                summary.firstSeenAt(),
                summary.lastSeenAt(),
                summary.sightingCount(),
                summary.maxConfidence(),
                summary.reviewedCount(),
                summary.falsePositive(),
                sightings));
    }

    @Override
    public Optional<Path> image(UUID sightingId, boolean thumbnail) {
        List<StoredImage> rows = jdbc.sql("""
                SELECT image_path, thumbnail_path
                FROM sightings
                WHERE sighting_id = :id
                """)
                .param("id", sightingId)
                .query((rs, row) -> new StoredImage(rs.getString("image_path"), rs.getString("thumbnail_path")))
                .list();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        StoredImage stored = rows.get(0);
        if (thumbnail) {
            Optional<Path> thumb = existingFile(stored.thumbnailPath());
            if (thumb.isPresent()) {
                return thumb;
            }
        }
        return existingFile(stored.imagePath());
    }

    @Override
    public List<ReviewCandidate> nextUnreviewed(int limit, Instant afterCapturedAt, UUID afterId, Instant neglectCutoff) {
        String cursor = "";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("limit", limit);
        if (afterCapturedAt != null) {
            cursor = """
                     AND (
                       s.captured_at > :afterCapturedAt
                       OR (s.captured_at = :afterCapturedAt AND s.sighting_id > :afterId)
                     )
                    """;
            params.put("afterCapturedAt", afterCapturedAt);
            params.put("afterId", afterId);
        }
        return statement("""
                SELECT s.sighting_id, s.client_id, s.captured_at, s.received_at, s.lat, s.lon,
                       s.speed, s.horizontal_accuracy_m, s.confidence,
                       s.bbox_x, s.bbox_y, s.bbox_width, s.bbox_height, s.image_path,
                       p.id AS pothole_id, p.lat AS pothole_lat, p.lon AS pothole_lon,
                       p.status AS pothole_status, p.first_seen_at AS pothole_first_seen_at, p.sighting_count
                FROM sightings s
                JOIN potholes p ON p.id = s.pothole_id
                WHERE s.review IS NULL
                """ + cursor + """
                 ORDER BY s.captured_at, s.sighting_id
                 LIMIT :limit
                """, params)
                .query((rs, row) -> new ReviewCandidate(
                        rs.getObject("sighting_id", UUID.class),
                        rs.getString("client_id"),
                        instant(rs, "captured_at"),
                        instant(rs, "received_at"),
                        rs.getDouble("lat"),
                        rs.getDouble("lon"),
                        rs.getDouble("speed"),
                        rs.getDouble("horizontal_accuracy_m"),
                        rs.getDouble("confidence"),
                        bbox(rs),
                        fileExists(rs.getString("image_path")),
                        rs.getObject("pothole_id", UUID.class),
                        rs.getDouble("pothole_lat"),
                        rs.getDouble("pothole_lon"),
                        rs.getString("pothole_status"),
                        neglected(rs.getString("pothole_status"), instant(rs, "pothole_first_seen_at"), neglectCutoff),
                        rs.getInt("sighting_count")))
                .list();
    }

    @Override
    public boolean setReview(UUID sightingId, String verdict, Instant reviewedAt) {
        int updated;
        if (verdict == null) {
            updated = jdbc.sql("""
                    UPDATE sightings
                    SET review = NULL, reviewed_by = NULL, reviewed_at = NULL
                    WHERE sighting_id = :id
                    """)
                    .param("id", sightingId)
                    .update();
        } else {
            updated = jdbc.sql("""
                    UPDATE sightings
                    SET review = :review, reviewed_by = NULL, reviewed_at = :reviewedAt
                    WHERE sighting_id = :id
                    """)
                    .param("review", verdict)
                    .param("reviewedAt", reviewedAt.atOffset(ZoneOffset.UTC))
                    .param("id", sightingId)
                    .update();
        }
        return updated == 1;
    }

    @Override
    public DashboardStats stats(Instant neglectCutoff) {
        long[] counts = statement("""
                SELECT
                    COUNT(*) FILTER (WHERE status = 'open') AS open_count,
                    COUNT(*) FILTER (WHERE status = 'open' AND first_seen_at <= :cutoff) AS neglected_count,
                    COUNT(*) FILTER (WHERE status = 'fixed') AS fixed_count,
                    COUNT(*) FILTER (WHERE status = 'resunk') AS resunk_count
                FROM potholes
                """, Map.of("cutoff", neglectCutoff))
                .query((rs, row) -> new long[] {
                        rs.getLong("open_count"),
                        rs.getLong("neglected_count"),
                        rs.getLong("fixed_count"),
                        rs.getLong("resunk_count")
                })
                .single();
        long[] reviews = jdbc.sql("""
                SELECT
                    COUNT(*) FILTER (WHERE review IS NOT NULL) AS reviewed,
                    COUNT(*) FILTER (WHERE review IS NULL) AS pending,
                    COUNT(*) FILTER (WHERE review = 'pothole') AS confirmed
                FROM sightings
                """)
                .query((rs, row) -> new long[] {
                        rs.getLong("reviewed"),
                        rs.getLong("pending"),
                        rs.getLong("confirmed")
                })
                .single();
        Double precision = reviews[0] == 0 ? null : (double) reviews[2] / reviews[0];
        return new DashboardStats(counts[0], counts[1], counts[2], counts[3], reviews[0], reviews[1], precision);
    }

    @Override
    public FleetStatus fleet() {
        Instant last = jdbc.sql("SELECT MAX(received_at) AS last_received FROM sightings")
                .query((rs, row) -> instant(rs, "last_received"))
                .single();
        int burst = 0;
        if (last != null) {
            burst = statement("""
                    SELECT COUNT(*) AS n FROM sightings WHERE received_at >= :since
                    """, Map.of("since", last.minus(LAST_SYNC_WINDOW)))
                    .query((rs, row) -> rs.getInt("n"))
                    .single();
        }
        List<ClientStatus> clients = jdbc.sql("""
                SELECT client_id, last_seen_at, queue_size
                FROM clients
                ORDER BY last_seen_at DESC, client_id
                """)
                .query((rs, row) -> {
                    Number queueSize = (Number) rs.getObject("queue_size");
                    return new ClientStatus(
                            rs.getString("client_id"),
                            instant(rs, "last_seen_at"),
                            queueSize == null ? null : queueSize.intValue());
                })
                .list();
        return new FleetStatus(last, burst, clients);
    }

    @Override
    public void writeSightingsCsv(PotholeCriteria criteria, Writer writer) {
        Sql where = where(criteria, false);
        try {
            writer.write("""
                    sighting_id,client_id,captured_at,received_at,lat,lon,heading,speed,\
                    horizontal_accuracy_m,confidence,bbox_x,bbox_y,bbox_width,bbox_height,\
                    pothole_id,review,reviewed_by,reviewed_at
                    """);
            writer.write("\n");
            statement("""
                    SELECT s.sighting_id, s.client_id, s.captured_at, s.received_at, s.lat, s.lon,
                           s.heading, s.speed, s.horizontal_accuracy_m, s.confidence,
                           s.bbox_x, s.bbox_y, s.bbox_width, s.bbox_height,
                           s.pothole_id, s.review, s.reviewed_by, s.reviewed_at
                    """ + " FROM sightings s JOIN potholes p ON p.id = s.pothole_id LEFT JOIN ("
                    + """
                            SELECT pothole_id,
                                   COUNT(*) FILTER (WHERE review IS NOT NULL)::int AS reviewed_count,
                                   COUNT(*) FILTER (WHERE review = 'false_positive')::int AS false_positive_count,
                                   COUNT(*) FILTER (WHERE review IS NULL)::int AS unreviewed_count
                            FROM sightings
                            GROUP BY pothole_id
                            ) agg ON agg.pothole_id = p.id
                    """
                    + where.sql()
                    + " ORDER BY s.captured_at, s.sighting_id", where.params())
                    .query((ResultSetExtractor<Boolean>) rs -> {
                        try {
                            while (rs.next()) {
                                writer.write(String.join(",",
                                        csv(rs.getObject("sighting_id")),
                                        csv(rs.getString("client_id")),
                                        csv(instant(rs, "captured_at")),
                                        csv(instant(rs, "received_at")),
                                        csv(rs.getDouble("lat")),
                                        csv(rs.getDouble("lon")),
                                        csv(rs.getDouble("heading")),
                                        csv(rs.getDouble("speed")),
                                        csv(rs.getDouble("horizontal_accuracy_m")),
                                        csv(rs.getDouble("confidence")),
                                        csv(rs.getInt("bbox_x")),
                                        csv(rs.getInt("bbox_y")),
                                        csv(rs.getInt("bbox_width")),
                                        csv(rs.getInt("bbox_height")),
                                        csv(rs.getObject("pothole_id")),
                                        csv(rs.getString("review")),
                                        csv(rs.getObject("reviewed_by")),
                                        csv(instant(rs, "reviewed_at"))));
                                writer.write("\n");
                            }
                        } catch (IOException ex) {
                            throw new UncheckedIOException(ex);
                        }
                        return Boolean.TRUE;
                    });
            writer.flush();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private PotholeSummary summary(ResultSet rs, Instant neglectCutoff) throws SQLException {
        String status = rs.getString("status");
        int sightingCount = rs.getInt("sighting_count");
        int falsePositiveCount = rs.getInt("false_positive_count");
        return new PotholeSummary(
                rs.getObject("id", UUID.class),
                rs.getDouble("lat"),
                rs.getDouble("lon"),
                status,
                neglected(status, instant(rs, "first_seen_at"), neglectCutoff),
                instant(rs, "first_seen_at"),
                instant(rs, "last_seen_at"),
                sightingCount,
                rs.getDouble("max_confidence"),
                rs.getInt("reviewed_count"),
                sightingCount > 0 && falsePositiveCount == sightingCount,
                rs.getObject("thumbnail_sighting_id", UUID.class));
    }

    private static DashboardSighting sighting(ResultSet rs) throws SQLException {
        return new DashboardSighting(
                rs.getObject("sighting_id", UUID.class),
                rs.getString("client_id"),
                instant(rs, "captured_at"),
                instant(rs, "received_at"),
                rs.getDouble("lat"),
                rs.getDouble("lon"),
                rs.getDouble("heading"),
                rs.getDouble("speed"),
                rs.getDouble("horizontal_accuracy_m"),
                rs.getDouble("confidence"),
                bbox(rs),
                rs.getString("review"),
                instant(rs, "reviewed_at"),
                fileExists(rs.getString("image_path")));
    }

    private static int[] bbox(ResultSet rs) throws SQLException {
        return new int[] {
                rs.getInt("bbox_x"),
                rs.getInt("bbox_y"),
                rs.getInt("bbox_width"),
                rs.getInt("bbox_height")
        };
    }

    private static boolean neglected(String status, Instant firstSeenAt, Instant cutoff) {
        return "open".equals(status) && firstSeenAt != null && !firstSeenAt.isAfter(cutoff);
    }

    private static String sortColumn(String sort) {
        String column = SORTS.get(sort);
        if (column == null) {
            throw new InvalidDashboardQueryException("sort");
        }
        return column;
    }

    private Sql where(PotholeCriteria criteria, boolean withBounds) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> params = new LinkedHashMap<>();
        if (criteria.status() != null) {
            sql.append(" AND p.status = :status");
            params.put("status", criteria.status());
        }
        if (Boolean.TRUE.equals(criteria.neglected())) {
            sql.append(" AND p.status = 'open' AND p.first_seen_at <= :neglectCutoff");
            params.put("neglectCutoff", criteria.neglectCutoff());
        } else if (Boolean.FALSE.equals(criteria.neglected())) {
            sql.append(" AND NOT (p.status = 'open' AND p.first_seen_at <= :neglectCutoff)");
            params.put("neglectCutoff", criteria.neglectCutoff());
        }
        if (criteria.from() != null) {
            sql.append(" AND p.last_seen_at >= :from");
            params.put("from", criteria.from());
        }
        if (criteria.to() != null) {
            sql.append(" AND p.last_seen_at <= :to");
            params.put("to", criteria.to());
        }
        if (criteria.minConfidence() != null) {
            sql.append(" AND p.max_confidence >= :minConfidence");
            params.put("minConfidence", criteria.minConfidence());
        }
        if (criteria.unreviewedOnly()) {
            sql.append(" AND COALESCE(agg.unreviewed_count, 0) > 0");
        }
        if (!criteria.includeFalsePositives()) {
            sql.append("""
                     AND NOT (
                       p.sighting_count > 0
                       AND COALESCE(agg.false_positive_count, 0) = p.sighting_count
                     )
                    """);
        }
        if (withBounds) {
            sql.append("""
                     AND p.lon BETWEEN :minLon AND :maxLon
                     AND p.lat BETWEEN :minLat AND :maxLat
                    """);
            params.put("minLon", criteria.minLon());
            params.put("minLat", criteria.minLat());
            params.put("maxLon", criteria.maxLon());
            params.put("maxLat", criteria.maxLat());
        }
        return new Sql(sql.toString(), params);
    }

    private JdbcClient.StatementSpec statement(String sql, Map<String, Object> params) {
        JdbcClient.StatementSpec spec = jdbc.sql(sql);
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Instant instant) {
                spec = spec.param(entry.getKey(), instant.atOffset(ZoneOffset.UTC));
            } else {
                spec = spec.param(entry.getKey(), value);
            }
        }
        return spec;
    }

    private static Optional<Path> existingFile(String path) {
        if (!fileExists(path)) {
            return Optional.empty();
        }
        return Optional.of(Path.of(path));
    }

    private static boolean fileExists(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        try {
            return Files.isRegularFile(Path.of(path));
        } catch (InvalidPathException ex) {
            return false;
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private record Sql(String sql, Map<String, Object> params) {
    }

    private record StoredImage(String imagePath, String thumbnailPath) {
    }
}
