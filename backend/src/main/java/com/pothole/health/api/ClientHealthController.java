package com.pothole.health.api;

import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import com.pothole.client.domain.ClientRegistry;
import com.pothole.health.domain.DatabaseProbe;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ClientHealthController {

    static final String CLIENT_ID = "X-Client-Id";

    static final String QUEUE_SIZE = "X-Queue-Size";

    private final DatabaseProbe database;

    private final ClientRegistry clients;

    private final Clock clock;

    ClientHealthController(DatabaseProbe database, ClientRegistry clients, Clock clock) {
        this.database = database;
        this.clients = clients;
        this.clock = clock;
    }

    @GetMapping("/v1/health")
    ResponseEntity<Map<String, String>> health(HttpServletRequest request) {
        if (!database.selectOne()) {
            return ResponseEntity.status(503).body(Map.of(
                    "service", "pothole-api",
                    "status", "db_unavailable"));
        }
        rememberClient(request);
        return ResponseEntity.ok(Map.of(
                "service", "pothole-api",
                "status", "ok",
                "time", DateTimeFormatter.ISO_INSTANT.format(clock.instant())));
    }

    private void rememberClient(HttpServletRequest request) {
        String clientId = request.getHeader(CLIENT_ID);
        if (clientId == null || clientId.isBlank()) {
            return;
        }
        clients.upsert(clientId, clock.instant(), queueSize(request.getHeader(QUEUE_SIZE)));
    }

    private static Integer queueSize(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(header);
            return parsed < 0 ? null : parsed;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
