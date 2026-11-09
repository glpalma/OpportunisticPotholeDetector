package com.pothole.ingest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ClientContractTest {

    private static final byte[] JPEG = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};

    private static final byte[] OTHER_JPEG = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

    private static final Path IMAGES = createImages();

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("pothole.api-key", () -> "test-key");
        registry.add("pothole.image-dir", () -> IMAGES.toString());
        registry.add("pothole.image.max-bytes", () -> "1024");
        registry.add("pothole.rate-limit.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void livenessDoesNotRequireAKey() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("up"));
    }

    @Test
    void clientHealthRequiresTheApiKeyAndRecordsTheBus() throws Exception {
        mockMvc.perform(get("/v1/health")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/health").header("X-API-Key", "wrong")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v1/health")
                        .header("X-API-Key", "test-key")
                        .header("X-Client-Id", "bus-1")
                        .header("X-Queue-Size", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("pothole-api"))
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.time", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")));

        mockMvc.perform(get("/v1/health")
                        .header("X-API-Key", "test-key")
                        .header("X-Client-Id", "bus-1"));

        assertEquals(7, queueSize("bus-1"));
    }

    @Test
    void listsStoredSightingsNewestCaptureFirst() throws Exception {
        mockMvc.perform(get("/v1/sightings")).andExpect(status().isUnauthorized());

        UUID olderId = UUID.randomUUID();
        UUID newerId = UUID.randomUUID();
        Instant older = Instant.now().minus(4, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant newer = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        post(olderId, "bus-1", -23.55, -46.63, older, 0.5, JPEG).andExpect(status().isCreated());
        post(newerId, "bus-2", -23.56, -46.64, newer, 0.5, JPEG).andExpect(status().isCreated());

        String body = mockMvc.perform(get("/v1/sightings")
                        .header("X-API-Key", "test-key")
                        .param("page", "1")
                        .param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.sightingId == '%s')].clientId".formatted(olderId)).value(hasItem("bus-1")))
                .andExpect(jsonPath("$[?(@.sightingId == '%s')].capturedAt".formatted(olderId))
                        .value(hasItem(DateTimeFormatter.ISO_INSTANT.format(older))))
                .andExpect(jsonPath("$[?(@.sightingId == '%s')].lat".formatted(olderId)).value(hasItem(-23.55)))
                .andExpect(jsonPath("$[?(@.sightingId == '%s')].lon".formatted(olderId)).value(hasItem(-46.63)))
                .andExpect(jsonPath("$[?(@.sightingId == '%s')].heading".formatted(olderId)).value(hasItem(180.0)))
                .andExpect(jsonPath("$[?(@.sightingId == '%s')].speed".formatted(olderId)).value(hasItem(12.5)))
                .andExpect(jsonPath("$[?(@.sightingId == '%s')].horizontalAccuracyM".formatted(olderId)).value(hasItem(3.0)))
                .andExpect(jsonPath("$[?(@.sightingId == '%s')].confidence".formatted(olderId)).value(hasItem(0.5)))
                .andExpect(jsonPath("$[?(@.sightingId == '%s')].bbox[0]".formatted(olderId)).value(hasItem(1)))
                .andExpect(jsonPath("$[?(@.sightingId == '%s')].potholeId".formatted(olderId))
                        .value(hasItem(potholeId(olderId).toString())))
                .andExpect(content().string(not(containsString("imagePath"))))
                .andExpect(content().string(not(containsString("thumbnail"))))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(body.indexOf(olderId.toString()) < body.indexOf(newerId.toString()));

        mockMvc.perform(get("/v1/sightings")
                        .header("X-API-Key", "test-key")
                        .param("page", "1")
                        .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sightingId").value(firstSightingId()));

        mockMvc.perform(get("/v1/sightings")
                        .header("X-API-Key", "test-key")
                        .param("page", "2")
                        .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sightingId").value(sightingIdAt(1)));

        mockMvc.perform(get("/v1/sightings").header("X-API-Key", "test-key").param("page", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/sightings").header("X-API-Key", "test-key").param("pageSize", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/sightings").header("X-API-Key", "test-key").param("pageSize", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void storesASightingOnceAndKeepsTheFirstImage() throws Exception {
        UUID sightingId = UUID.randomUUID();
        Instant capturedAt = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        post(sightingId, "bus-1", -23.55, -46.63, capturedAt, 0.8, JPEG).andExpect(status().isCreated());
        post(sightingId, "bus-1", -23.55, -46.63, capturedAt, 0.8, OTHER_JPEG).andExpect(status().isOk());

        assertEquals(1L, count("SELECT count(*) FROM sightings WHERE sighting_id = :id", sightingId));
        assertEquals(1L, count("SELECT count(*) FROM jobs WHERE sighting_id = :id AND type = 'thumbnail' AND status = 'pending'", sightingId));
        String imagePath = jdbc.sql("SELECT image_path FROM sightings WHERE sighting_id = :id")
                .param("id", sightingId)
                .query(String.class)
                .single();
        assertArrayEquals(JPEG, Files.readAllBytes(Path.of(imagePath)));
        assertFalse(Files.exists(Path.of(imagePath).resolveSibling(sightingId + ".jpg.part")));
    }

    @Test
    void rejectsBadMetadataAndBadImages() throws Exception {
        UUID sightingId = UUID.randomUUID();
        Instant capturedAt = Instant.now().minus(1, ChronoUnit.HOURS);

        post(sightingId, "bus-1", 0, 0, capturedAt, 0.5, JPEG).andExpect(status().isBadRequest());
        post(UUID.randomUUID(), "bus-1", -23.55, -46.63, capturedAt, 0.5, new byte[] {1, 2, 3, 4})
                .andExpect(status().isUnsupportedMediaType());
        post(UUID.randomUUID(), "bus-1", -23.55, -46.63, capturedAt, 0.5, new byte[2048])
                .andExpect(status().isContentTooLarge());

        mockMvc.perform(multipart("/v1/sightings")
                        .file(new MockMultipartFile("metadata", "metadata.json", "application/json", "{}".getBytes(StandardCharsets.UTF_8)))
                        .header("X-API-Key", "test-key"))
                .andExpect(status().isUnsupportedMediaType());
        assertEquals(0L, count("SELECT count(*) FROM sightings WHERE sighting_id = :id", sightingId));
    }

    @Test
    void clustersNearbySightingsAndMarksAFixedPotholeResunk() throws Exception {
        Instant earlier = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant later = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID far = UUID.randomUUID();
        double lat = -23.0;
        double lon = -46.0;

        post(first, "bus-1", lat, lon, earlier, 0.4, JPEG).andExpect(status().isCreated());
        post(second, "bus-1", lat + (4.0 / 111_320.0), lon, later, 0.9, JPEG).andExpect(status().isCreated());
        post(far, "bus-1", lat + 0.01, lon, later, 0.5, JPEG).andExpect(status().isCreated());

        UUID potholeId = potholeId(first);
        assertEquals(potholeId, potholeId(second));
        assertFalse(potholeId.equals(potholeId(far)));
        assertEquals(2, sightingCount(potholeId));
        assertEquals(0.9, maxConfidence(potholeId));
        assertEquals(lat, potholeLat(potholeId));
        assertEquals(earlier, firstSeen(potholeId));
        assertEquals(later, lastSeen(potholeId));
        assertEquals("open", potholeStatus(potholeId));

        UUID fixedId = UUID.randomUUID();
        OffsetDateTime seen = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        jdbc.sql("""
                INSERT INTO potholes (
                    id, lat, lon, status, first_seen_at, last_seen_at, sighting_count, max_confidence, created_at
                ) VALUES (
                    :id, -22.0, -45.0, 'fixed', :seen, :seen, 1, 0.4, :seen
                )
                """)
                .param("id", fixedId)
                .param("seen", seen)
                .update();
        post(UUID.randomUUID(), "bus-1", -22.0, -45.0, later, 0.6, JPEG).andExpect(status().isCreated());
        assertEquals("resunk", potholeStatus(fixedId));
        assertEquals(2, sightingCount(fixedId));
        assertEquals(-22.0, potholeLat(fixedId));
    }

    private org.springframework.test.web.servlet.ResultActions post(UUID sightingId, String clientId, double lat, double lon,
            Instant capturedAt, double confidence, byte[] image) throws Exception {
        String metadata = """
                {"sighting_id":"%s","client_id":"%s","lat":%s,"lon":%s,"capturedAt":"%s","heading":180,"speed":12.5,"horizontal_accuracy_m":3,"confidence":%s,"bbox":[1,2,30,40]}
                """.formatted(sightingId, clientId, lat, lon, DateTimeFormatter.ISO_INSTANT.format(capturedAt), confidence);
        return mockMvc.perform(multipart("/v1/sightings")
                .file(new MockMultipartFile("metadata", "metadata.json", "application/json", metadata.getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("image", "frame.jpg", "image/jpeg", image))
                .header("X-API-Key", "test-key"));
    }

    private int queueSize(String clientId) {
        return jdbc.sql("SELECT queue_size FROM clients WHERE client_id = :id")
                .param("id", clientId)
                .query(Integer.class)
                .single();
    }

    private long count(String sql, UUID sightingId) {
        return jdbc.sql(sql).param("id", sightingId).query(Long.class).single();
    }

    private String firstSightingId() {
        return sightingIdAt(0);
    }

    private String sightingIdAt(int offset) {
        return jdbc.sql("""
                SELECT sighting_id FROM sightings
                ORDER BY captured_at DESC, sighting_id
                LIMIT 1 OFFSET :offset
                """)
                .param("offset", offset)
                .query(UUID.class)
                .single()
                .toString();
    }

    private UUID potholeId(UUID sightingId) {
        return jdbc.sql("SELECT pothole_id FROM sightings WHERE sighting_id = :id")
                .param("id", sightingId)
                .query((rs, row) -> rs.getObject(1, UUID.class))
                .single();
    }

    private int sightingCount(UUID potholeId) {
        return jdbc.sql("SELECT sighting_count FROM potholes WHERE id = :id")
                .param("id", potholeId)
                .query(Integer.class)
                .single();
    }

    private double maxConfidence(UUID potholeId) {
        return jdbc.sql("SELECT max_confidence FROM potholes WHERE id = :id")
                .param("id", potholeId)
                .query(Double.class)
                .single();
    }

    private double potholeLat(UUID potholeId) {
        return jdbc.sql("SELECT lat FROM potholes WHERE id = :id")
                .param("id", potholeId)
                .query(Double.class)
                .single();
    }

    private Instant firstSeen(UUID potholeId) {
        return seen(potholeId, "first_seen_at");
    }

    private Instant lastSeen(UUID potholeId) {
        return seen(potholeId, "last_seen_at");
    }

    private Instant seen(UUID potholeId, String column) {
        OffsetDateTime value = jdbc.sql("SELECT " + column + " FROM potholes WHERE id = :id")
                .param("id", potholeId)
                .query(OffsetDateTime.class)
                .single();
        return value.toInstant();
    }

    private String potholeStatus(UUID potholeId) {
        return jdbc.sql("SELECT status FROM potholes WHERE id = :id")
                .param("id", potholeId)
                .query(String.class)
                .single();
    }

    private static Path createImages() {
        try {
            return Files.createTempDirectory("pothole-images");
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }
}
