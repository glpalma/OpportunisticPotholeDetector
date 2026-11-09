package com.pothole.sighting.data;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.pothole.sighting.domain.Sighting;
import com.pothole.sighting.domain.SightingMetadata;
import com.pothole.sighting.domain.SightingStore;

import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class JdbcSightingStore implements SightingStore {

    private static final long CLUSTER_LOCK = 7_420_111L;

    private static final double METERS_PER_DEGREE = 111_320d;

    private final JdbcClient jdbc;

    JdbcSightingStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Sighting> list(int limit, long offset) {
        return jdbc.sql("""
                SELECT sighting_id, client_id, captured_at, received_at,
                       lat, lon, heading, speed, horizontal_accuracy_m, confidence,
                       bbox_x, bbox_y, bbox_width, bbox_height,
                       pothole_id, review, reviewed_by, reviewed_at
                FROM sightings
                ORDER BY captured_at DESC, sighting_id
                LIMIT :limit OFFSET :offset
                """)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, row) -> sighting(rs))
                .list();
    }

    @Override
    public void lockClustering() {
        jdbc.sql("SELECT pg_advisory_xact_lock(:key)")
                .param("key", CLUSTER_LOCK)
                .query((ResultSetExtractor<Boolean>) rs -> rs.next());
    }

    @Override
    public boolean insertSighting(SightingMetadata metadata, Instant receivedAt, String imagePath) {
        int inserted = jdbc.sql("""
                INSERT INTO sightings (
                    sighting_id, client_id, captured_at, received_at,
                    lat, lon, heading, speed, horizontal_accuracy_m, confidence,
                    bbox_x, bbox_y, bbox_width, bbox_height, image_path
                ) VALUES (
                    :sightingId, :clientId, :capturedAt, :receivedAt,
                    :lat, :lon, :heading, :speed, :horizontalAccuracyM, :confidence,
                    :bboxX, :bboxY, :bboxWidth, :bboxHeight, :imagePath
                )
                ON CONFLICT (sighting_id) DO NOTHING
                """)
                .param("sightingId", metadata.sightingId())
                .param("clientId", metadata.clientId())
                .param("capturedAt", metadata.capturedAt().atOffset(ZoneOffset.UTC))
                .param("receivedAt", receivedAt.atOffset(ZoneOffset.UTC))
                .param("lat", metadata.lat())
                .param("lon", metadata.lon())
                .param("heading", metadata.heading())
                .param("speed", metadata.speed())
                .param("horizontalAccuracyM", metadata.horizontalAccuracyM())
                .param("confidence", metadata.confidence())
                .param("bboxX", metadata.bboxX())
                .param("bboxY", metadata.bboxY())
                .param("bboxWidth", metadata.bboxWidth())
                .param("bboxHeight", metadata.bboxHeight())
                .param("imagePath", imagePath)
                .update();
        return inserted == 1;
    }

    @Override
    public UUID attachOrCreate(SightingMetadata metadata, Instant receivedAt) {
        double lat = metadata.lat();
        double lon = metadata.lon();
        double latPad = 15 / METERS_PER_DEGREE;
        double lonPad = 15 / Math.max(1d, METERS_PER_DEGREE * Math.cos(Math.toRadians(lat)));
        List<Nearest> nearest = jdbc.sql("""
                SELECT id,
                       6371000 * 2 * asin(least(1.0, sqrt(
                           power(sin(radians(lat - :lat) / 2), 2)
                           + cos(radians(:lat)) * cos(radians(lat))
                             * power(sin(radians(lon - :lon) / 2), 2)
                       ))) AS distance_m
                FROM potholes
                WHERE lat BETWEEN :minLat AND :maxLat
                  AND lon BETWEEN :minLon AND :maxLon
                ORDER BY distance_m
                LIMIT 1
                """)
                .param("lat", lat)
                .param("lon", lon)
                .param("minLat", lat - latPad)
                .param("maxLat", lat + latPad)
                .param("minLon", lon - lonPad)
                .param("maxLon", lon + lonPad)
                .query((rs, row) -> new Nearest(rs.getObject("id", UUID.class), rs.getDouble("distance_m")))
                .list();
        if (nearest.isEmpty() || nearest.get(0).distanceMeters() > SightingStore.RADIUS_METERS) {
            return createPothole(metadata, receivedAt);
        }
        updatePothole(nearest.get(0).id(), metadata);
        return nearest.get(0).id();
    }

    @Override
    public void assignPothole(UUID sightingId, UUID potholeId) {
        jdbc.sql("UPDATE sightings SET pothole_id = :potholeId WHERE sighting_id = :sightingId")
                .param("potholeId", potholeId)
                .param("sightingId", sightingId)
                .update();
    }

    @Override
    public void enqueueThumbnail(UUID sightingId, Instant receivedAt) {
        jdbc.sql("""
                INSERT INTO jobs (id, type, sighting_id, status, attempts, run_after, created_at)
                VALUES (:id, 'thumbnail', :sightingId, 'pending', 0, :runAfter, :createdAt)
                """)
                .param("id", UUID.randomUUID())
                .param("sightingId", sightingId)
                .param("runAfter", receivedAt.atOffset(ZoneOffset.UTC))
                .param("createdAt", receivedAt.atOffset(ZoneOffset.UTC))
                .update();
    }

    private UUID createPothole(SightingMetadata metadata, Instant receivedAt) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO potholes (
                    id, lat, lon, status, first_seen_at, last_seen_at,
                    sighting_count, max_confidence, created_at
                ) VALUES (
                    :id, :lat, :lon, 'open', :capturedAt, :capturedAt,
                    1, :confidence, :createdAt
                )
                """)
                .param("id", id)
                .param("lat", metadata.lat())
                .param("lon", metadata.lon())
                .param("capturedAt", metadata.capturedAt().atOffset(ZoneOffset.UTC))
                .param("confidence", metadata.confidence())
                .param("createdAt", receivedAt.atOffset(ZoneOffset.UTC))
                .update();
        return id;
    }

    private void updatePothole(UUID id, SightingMetadata metadata) {
        jdbc.sql("""
                UPDATE potholes
                SET sighting_count = sighting_count + 1,
                    max_confidence = GREATEST(max_confidence, :confidence),
                    first_seen_at = LEAST(first_seen_at, :capturedAt),
                    last_seen_at = GREATEST(last_seen_at, :capturedAt),
                    status = CASE WHEN status = 'fixed' THEN 'resunk' ELSE status END
                WHERE id = :id
                """)
                .param("confidence", metadata.confidence())
                .param("capturedAt", metadata.capturedAt().atOffset(ZoneOffset.UTC))
                .param("id", id)
                .update();
    }

    private static Sighting sighting(ResultSet rs) throws SQLException {
        return new Sighting(
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
                rs.getInt("bbox_x"),
                rs.getInt("bbox_y"),
                rs.getInt("bbox_width"),
                rs.getInt("bbox_height"),
                rs.getObject("pothole_id", UUID.class),
                rs.getString("review"),
                rs.getObject("reviewed_by", UUID.class),
                instant(rs, "reviewed_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private record Nearest(UUID id, double distanceMeters) {
    }
}
