package com.pothole.ingest;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RateLimitTest {

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
        registry.add("pothole.rate-limit.enabled", () -> "true");
        registry.add("pothole.rate-limit.requests-per-second", () -> "1");
        registry.add("pothole.rate-limit.burst", () -> "3");
        registry.add("pothole.rate-limit.auth-failures-per-minute", () -> "2");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void burstsPastTheRequestLimitAre429() throws Exception {
        mockMvc.perform(health("10.1.0.1", "test-key")).andExpect(status().isOk());
        var limited = mockMvc.perform(health("10.1.0.1", "test-key"));
        for (int i = 0; i < 8 && limited.andReturn().getResponse().getStatus() != 429; i++) {
            limited = mockMvc.perform(health("10.1.0.1", "test-key"));
        }
        limited.andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void repeatedBadKeysAre429() throws Exception {
        mockMvc.perform(health("10.2.0.2", "wrong")).andExpect(status().isUnauthorized());
        mockMvc.perform(health("10.2.0.2", "wrong")).andExpect(status().isUnauthorized());
        mockMvc.perform(health("10.2.0.2", "wrong"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    private static MockHttpServletRequestBuilder health(String ip, String apiKey) {
        return get("/v1/health")
                .header("X-API-Key", apiKey)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                });
    }

    private static Path createImages() {
        try {
            return Files.createTempDirectory("pothole-images");
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }
}
