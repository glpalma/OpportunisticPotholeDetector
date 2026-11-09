package com.pothole.sighting.domain;

import java.time.Instant;
import java.util.UUID;

public record Sighting(
        UUID sightingId,
        String clientId,
        Instant capturedAt,
        Instant receivedAt,
        double lat,
        double lon,
        double heading,
        double speed,
        double horizontalAccuracyM,
        double confidence,
        int bboxX,
        int bboxY,
        int bboxWidth,
        int bboxHeight,
        UUID potholeId,
        String review,
        UUID reviewedBy,
        Instant reviewedAt) {
}
