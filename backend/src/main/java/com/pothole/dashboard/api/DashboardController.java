package com.pothole.dashboard.api;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.pothole.config.PotholeProperties;
import com.pothole.dashboard.domain.DashboardRead;
import com.pothole.dashboard.domain.DashboardStats;
import com.pothole.dashboard.domain.FleetStatus;
import com.pothole.dashboard.domain.InvalidDashboardQueryException;
import com.pothole.dashboard.domain.MapPoint;
import com.pothole.dashboard.domain.PotholeCriteria;
import com.pothole.dashboard.domain.PotholeDetail;
import com.pothole.dashboard.domain.PotholePage;
import com.pothole.dashboard.domain.ReviewCandidate;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/app/api")
class DashboardController {

    static final int DEFAULT_PAGE = 1;

    static final int DEFAULT_PAGE_SIZE = 50;

    static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> STATUSES = Set.of("open", "fixed", "resunk");

    private static final Set<String> SORTS = Set.of(
            "lastSeenAt", "firstSeenAt", "sightingCount", "maxConfidence", "status", "reviewedCount");

    private static final CacheControl IMAGE_CACHE = CacheControl.maxAge(Duration.ofDays(1)).cachePrivate().immutable();

    private final DashboardRead dashboard;

    private final Clock clock;

    private final PotholeProperties properties;

    DashboardController(DashboardRead dashboard, Clock clock, PotholeProperties properties) {
        this.dashboard = dashboard;
        this.clock = clock;
        this.properties = properties;
    }

    @GetMapping("/potholes/map")
    List<MapPoint> map(
            @RequestParam String bounds,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean neglected,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Double minConfidence,
            @RequestParam(defaultValue = "false") boolean unreviewed,
            @RequestParam(defaultValue = "false") boolean includeFalsePositives) {
        double[] box = parseBounds(bounds);
        return dashboard.map(criteria(
                status, neglected, from, to, minConfidence, unreviewed, includeFalsePositives,
                box, "lastSeenAt", "desc", DashboardRead.MAP_LIMIT, 0));
    }

    @GetMapping("/potholes")
    PotholePage list(
            @RequestParam(defaultValue = "" + DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int pageSize,
            @RequestParam(defaultValue = "lastSeenAt") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean neglected,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Double minConfidence,
            @RequestParam(defaultValue = "false") boolean unreviewed,
            @RequestParam(defaultValue = "false") boolean includeFalsePositives) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new InvalidDashboardQueryException("page");
        }
        long offset = (long) (page - 1) * pageSize;
        return dashboard.list(criteria(
                status, neglected, from, to, minConfidence, unreviewed, includeFalsePositives,
                null, sort, direction, pageSize, offset));
    }

    @GetMapping("/potholes/{id}")
    ResponseEntity<PotholeDetail> detail(@PathVariable UUID id) {
        return dashboard.detail(id, neglectCutoff())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/sightings/{id}/image")
    ResponseEntity<FileSystemResource> image(@PathVariable UUID id) {
        return imageResponse(id, false);
    }

    @GetMapping("/sightings/{id}/thumbnail")
    ResponseEntity<FileSystemResource> thumbnail(@PathVariable UUID id) {
        return imageResponse(id, true);
    }

    @GetMapping("/review-queue/next")
    List<ReviewCandidate> next(
            @RequestParam(defaultValue = "2") int limit,
            @RequestParam(required = false) Instant afterCapturedAt,
            @RequestParam(required = false) UUID afterId) {
        if (limit < 1 || limit > 20) {
            throw new InvalidDashboardQueryException("limit");
        }
        if ((afterCapturedAt == null) != (afterId == null)) {
            throw new InvalidDashboardQueryException("cursor");
        }
        return dashboard.nextUnreviewed(limit, afterCapturedAt, afterId, neglectCutoff());
    }

    @PutMapping("/sightings/{id}/review")
    ResponseEntity<Void> review(@PathVariable UUID id, @RequestBody ReviewRequest body) {
        String verdict = body.verdict();
        if (verdict != null && !verdict.equals("pothole") && !verdict.equals("false_positive")) {
            throw new InvalidDashboardQueryException("verdict");
        }
        Instant reviewedAt = verdict == null ? null : clock.instant();
        if (!dashboard.setReview(id, verdict, reviewedAt)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    DashboardStats stats() {
        return dashboard.stats(neglectCutoff());
    }

    @GetMapping("/clients")
    FleetStatus clients() {
        return dashboard.fleet();
    }

    @GetMapping(path = "/export/sightings.csv", produces = "text/csv")
    ResponseEntity<StreamingResponseBody> export(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean neglected,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Double minConfidence,
            @RequestParam(defaultValue = "false") boolean unreviewed,
            @RequestParam(defaultValue = "false") boolean includeFalsePositives) {
        PotholeCriteria criteria = criteria(
                status, neglected, from, to, minConfidence, unreviewed, includeFalsePositives,
                null, "lastSeenAt", "desc", DEFAULT_PAGE_SIZE, 0);
        StreamingResponseBody body = output -> {
            try (OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                dashboard.writeSightingsCsv(criteria, writer);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sightings.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    private ResponseEntity<FileSystemResource> imageResponse(UUID id, boolean thumbnail) {
        return dashboard.image(id, thumbnail)
                .map(path -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(IMAGE_CACHE)
                        .body(new FileSystemResource(path)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Instant neglectCutoff() {
        return clock.instant().minus(Duration.ofDays(properties.getNeglectWindowDays()));
    }

    private PotholeCriteria criteria(
            String status,
            Boolean neglected,
            String from,
            String to,
            Double minConfidence,
            boolean unreviewed,
            boolean includeFalsePositives,
            double[] bounds,
            String sort,
            String direction,
            int limit,
            long offset) {
        String normalizedStatus = blankToNull(status);
        if (normalizedStatus != null && !STATUSES.contains(normalizedStatus)) {
            throw new InvalidDashboardQueryException("status");
        }
        if (!SORTS.contains(sort)) {
            throw new InvalidDashboardQueryException("sort");
        }
        if (!direction.equals("asc") && !direction.equals("desc")) {
            throw new InvalidDashboardQueryException("direction");
        }
        if (minConfidence != null && (minConfidence < 0 || minConfidence > 1)) {
            throw new InvalidDashboardQueryException("minConfidence");
        }
        Instant fromInstant = parseInstant(from);
        Instant toInstant = parseInstant(to);
        if (fromInstant != null && toInstant != null && fromInstant.isAfter(toInstant)) {
            throw new InvalidDashboardQueryException("from");
        }
        Double minLon = bounds == null ? null : bounds[0];
        Double minLat = bounds == null ? null : bounds[1];
        Double maxLon = bounds == null ? null : bounds[2];
        Double maxLat = bounds == null ? null : bounds[3];
        return new PotholeCriteria(
                normalizedStatus,
                neglected,
                fromInstant,
                toInstant,
                minConfidence,
                unreviewed,
                includeFalsePositives,
                neglectCutoff(),
                minLon,
                minLat,
                maxLon,
                maxLat,
                sort,
                direction.equals("asc"),
                limit,
                offset);
    }

    private static double[] parseBounds(String bounds) {
        String[] parts = bounds.split(",");
        if (parts.length != 4) {
            throw new InvalidDashboardQueryException("bounds");
        }
        try {
            double minLon = Double.parseDouble(parts[0].trim());
            double minLat = Double.parseDouble(parts[1].trim());
            double maxLon = Double.parseDouble(parts[2].trim());
            double maxLat = Double.parseDouble(parts[3].trim());
            if (!finite(minLon) || !finite(minLat) || !finite(maxLon) || !finite(maxLat)
                    || minLon < -180 || maxLon > 180 || minLat < -90 || maxLat > 90
                    || minLon >= maxLon || minLat >= maxLat) {
                throw new InvalidDashboardQueryException("bounds");
            }
            return new double[] {minLon, minLat, maxLon, maxLat};
        } catch (NumberFormatException ex) {
            throw new InvalidDashboardQueryException("bounds");
        }
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new InvalidDashboardQueryException("time");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
