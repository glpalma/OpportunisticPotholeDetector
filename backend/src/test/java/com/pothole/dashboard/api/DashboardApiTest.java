package com.pothole.dashboard.api;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.pothole.dashboard.domain.SilenceSweep;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DashboardApiTest {

    private static final byte[] JPEG = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};

    private static final Path IMAGES = createImages();

    private static final String BOUNDS = "-47.2,-23.1,-47.0,-22.8";

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("pothole.api-key", () -> "test-key");
        registry.add("pothole.image-dir", () -> IMAGES.toString());
        registry.add("pothole.rate-limit.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private SilenceSweep silenceSweep;

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM jobs").update();
        jdbc.sql("DELETE FROM sightings").update();
        jdbc.sql("DELETE FROM potholes").update();
        jdbc.sql("DELETE FROM clients").update();
    }

    @Test
    void dashboardReadsDoNotRequireAnApiKey() throws Exception {
        mockMvc.perform(get("/app/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(0))
                .andExpect(jsonPath("$.detectorPrecision").value(nullValue()));
    }

    @Test
    void mapTableDetailAndExportShareFilters() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        UUID neglected = pothole("open", now.minus(40, ChronoUnit.DAYS), now, 0.9);
        UUID fresh = pothole("open", now.minus(1, ChronoUnit.DAYS), now, 0.4);
        UUID falsePositive = pothole("open", now.minus(2, ChronoUnit.DAYS), now, 0.8);
        sighting(neglected, now.minus(40, ChronoUnit.DAYS), now, 0.9, null);
        sighting(fresh, now.minus(1, ChronoUnit.DAYS), now, 0.4, null);
        UUID falseSighting = sighting(falsePositive, now.minus(2, ChronoUnit.DAYS), now, 0.8, "false_positive");

        mockMvc.perform(get("/app/api/potholes/map").param("bounds", BOUNDS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == '%s')].neglected".formatted(neglected)).value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$[?(@.id == '%s')].neglected".formatted(fresh)).value(org.hamcrest.Matchers.hasItem(false)));

        mockMvc.perform(get("/app/api/potholes/map")
                        .param("bounds", BOUNDS)
                        .param("includeFalsePositives", "true"))
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(get("/app/api/potholes").param("neglected", "true").param("sort", "lastSeenAt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(neglected.toString()))
                .andExpect(jsonPath("$.items[0].reviewedCount").value(0))
                .andExpect(jsonPath("$.items[0].thumbnailSightingId").exists());

        mockMvc.perform(get("/app/api/potholes").param("minConfidence", "0.85"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(neglected.toString()));

        mockMvc.perform(get("/app/api/potholes/{id}", neglected))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.neglected").value(true))
                .andExpect(jsonPath("$.sightings.length()").value(1))
                .andExpect(jsonPath("$.sightings[0].confidence").value(closeTo(0.9, 0.001)))
                .andExpect(jsonPath("$.sightings[0].bbox.length()").value(4));

        mockMvc.perform(get("/app/api/potholes/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());

        MvcResult export = mockMvc.perform(get("/app/api/export/sightings.csv").param("minConfidence", "0.85"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("sightings.csv")))
                .andReturn();
        mockMvc.perform(asyncDispatch(export))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("sighting_id")))
                .andExpect(content().string(containsString(neglected.toString())))
                .andExpect(content().string(not(containsString("image_path"))))
                .andExpect(content().string(not(containsString(falseSighting.toString()))));
    }

    @Test
    void reviewQueueAndVerdictDoNotChangeThePothole() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        UUID potholeId = pothole("open", now.minus(2, ChronoUnit.DAYS), now, 0.7);
        UUID older = sighting(potholeId, now.minus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.HOURS), 0.7, null);
        UUID newer = sighting(potholeId, now.minus(1, ChronoUnit.DAYS), now, 0.6, null);
        Files.write(IMAGES.resolve(older + ".jpg"), JPEG);
        jdbc.sql("UPDATE sightings SET image_path = :path WHERE sighting_id = :id")
                .param("path", IMAGES.resolve(older + ".jpg").toString())
                .param("id", older)
                .update();

        mockMvc.perform(get("/app/api/review-queue/next").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sightingId").value(older.toString()))
                .andExpect(jsonPath("$[0].potholeId").value(potholeId.toString()))
                .andExpect(jsonPath("$[0].potholeSightingCount").value(2))
                .andExpect(jsonPath("$[0].imageAvailable").value(true));

        mockMvc.perform(get("/app/api/review-queue/next")
                        .param("afterCapturedAt", now.minus(2, ChronoUnit.DAYS).toString())
                        .param("afterId", older.toString()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sightingId").value(newer.toString()));

        mockMvc.perform(put("/app/api/sightings/{id}/review", older)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verdict\":\"pothole\"}"))
                .andExpect(status().isNoContent());

        assertEquals("pothole", reviewOf(older));
        assertNull(reviewedBy(older));
        assertEquals("open", potholeStatus(potholeId));
        assertEquals(2, sightingCount(potholeId));

        mockMvc.perform(get("/app/api/stats"))
                .andExpect(jsonPath("$.sightingsReviewed").value(1))
                .andExpect(jsonPath("$.sightingsPending").value(1))
                .andExpect(jsonPath("$.detectorPrecision").value(closeTo(1.0, 0.001)));

        mockMvc.perform(put("/app/api/sightings/{id}/review", older)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verdict\":null}"))
                .andExpect(status().isNoContent());
        assertNull(reviewOf(older));

        mockMvc.perform(put("/app/api/sightings/{id}/review", older)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verdict\":\"maybe\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/app/api/sightings/{id}/review", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verdict\":\"pothole\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void thumbnailFallsBackToTheFullFrame() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        UUID potholeId = pothole("open", now, now, 0.5);
        UUID sightingId = sighting(potholeId, now, now, 0.5, null);
        Path image = IMAGES.resolve(sightingId + ".jpg");
        Files.write(image, JPEG);
        jdbc.sql("UPDATE sightings SET image_path = :path WHERE sighting_id = :id")
                .param("path", image.toString())
                .param("id", sightingId)
                .update();

        mockMvc.perform(get("/app/api/sightings/{id}/thumbnail", sightingId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(header().string("Cache-Control", containsString("immutable")))
                .andExpect(content().bytes(JPEG));

        mockMvc.perform(get("/app/api/sightings/{id}/image", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void clientsReportTheLatestSync() throws Exception {
        Instant seen = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        jdbc.sql("""
                INSERT INTO clients (client_id, last_seen_at, queue_size)
                VALUES ('bus-1', :seen, 4)
                """)
                .param("seen", seen.atOffset(ZoneOffset.UTC))
                .update();
        UUID potholeId = pothole("open", seen, seen, 0.5);
        sighting(potholeId, seen, seen, 0.5, null);

        mockMvc.perform(get("/app/api/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sightingsInLastSync").value(1))
                .andExpect(jsonPath("$.clients[0].clientId").value("bus-1"))
                .andExpect(jsonPath("$.clients[0].queueSize").value(4));
    }

    @Test
    void silenceSweepFixesQuietOpenPotholesOnly() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        UUID quiet = pothole("open", now.minus(40, ChronoUnit.DAYS), now.minus(20, ChronoUnit.DAYS), 0.5);
        UUID recent = pothole("open", now.minus(3, ChronoUnit.DAYS), now, 0.5);
        UUID comeback = pothole("resunk", now.minus(50, ChronoUnit.DAYS), now.minus(40, ChronoUnit.DAYS), 0.5);

        assertEquals(1, silenceSweep.apply());
        assertEquals("fixed", potholeStatus(quiet));
        assertEquals("open", potholeStatus(recent));
        assertEquals("resunk", potholeStatus(comeback));
    }

    private UUID pothole(String status, Instant firstSeen, Instant lastSeen, double confidence) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO potholes (
                    id, lat, lon, status, first_seen_at, last_seen_at,
                    sighting_count, max_confidence, created_at
                ) VALUES (
                    :id, -22.9056, -47.0608, :status, :firstSeen, :lastSeen,
                    0, :confidence, :createdAt
                )
                """)
                .param("id", id)
                .param("status", status)
                .param("firstSeen", firstSeen.atOffset(ZoneOffset.UTC))
                .param("lastSeen", lastSeen.atOffset(ZoneOffset.UTC))
                .param("confidence", confidence)
                .param("createdAt", lastSeen.atOffset(ZoneOffset.UTC))
                .update();
        return id;
    }

    private UUID sighting(UUID potholeId, Instant capturedAt, Instant receivedAt, double confidence, String review) {
        jdbc.sql("""
                INSERT INTO clients (client_id, last_seen_at, queue_size)
                VALUES ('bus-1', :seen, 1)
                ON CONFLICT (client_id) DO NOTHING
                """)
                .param("seen", receivedAt.atOffset(ZoneOffset.UTC))
                .update();
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO sightings (
                    sighting_id, client_id, captured_at, received_at, lat, lon, heading, speed,
                    horizontal_accuracy_m, confidence, bbox_x, bbox_y, bbox_width, bbox_height,
                    image_path, pothole_id, review, reviewed_at
                ) VALUES (
                    :id, 'bus-1', :capturedAt, :receivedAt, -22.9056, -47.0608, 90, 8,
                    4, :confidence, 10, 20, 30, 40,
                    :imagePath, :potholeId, :review, :reviewedAt
                )
                """)
                .param("id", id)
                .param("capturedAt", capturedAt.atOffset(ZoneOffset.UTC))
                .param("receivedAt", receivedAt.atOffset(ZoneOffset.UTC))
                .param("confidence", confidence)
                .param("imagePath", IMAGES.resolve("missing.jpg").toString())
                .param("potholeId", potholeId)
                .param("review", review, Types.VARCHAR)
                .param("reviewedAt", review == null ? null : receivedAt.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .update();
        jdbc.sql("""
                UPDATE potholes
                SET sighting_count = sighting_count + 1
                WHERE id = :id
                """)
                .param("id", potholeId)
                .update();
        return id;
    }

    private String reviewOf(UUID sightingId) {
        return jdbc.sql("SELECT review FROM sightings WHERE sighting_id = :id")
                .param("id", sightingId)
                .query((rs, row) -> rs.getString("review"))
                .list()
                .stream()
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }

    private UUID reviewedBy(UUID sightingId) {
        return jdbc.sql("SELECT reviewed_by FROM sightings WHERE sighting_id = :id")
                .param("id", sightingId)
                .query((rs, row) -> rs.getString("reviewed_by"))
                .list()
                .stream()
                .filter(value -> value != null)
                .map(UUID::fromString)
                .findFirst()
                .orElse(null);
    }

    private String potholeStatus(UUID id) {
        return jdbc.sql("SELECT status FROM potholes WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .single();
    }

    private int sightingCount(UUID id) {
        return jdbc.sql("SELECT sighting_count FROM potholes WHERE id = :id")
                .param("id", id)
                .query(Integer.class)
                .single();
    }

    private static Path createImages() {
        try {
            return Files.createTempDirectory("pothole-dashboard-images");
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
