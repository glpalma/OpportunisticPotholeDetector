package com.pothole.sighting.domain;

import java.time.Instant;
import java.util.UUID;

public record SightingMetadata(
        UUID sightingId,
        String clientId,
        Instant capturedAt,
        double lat,
        double lon,
        double heading,
        double speed,
        double horizontalAccuracyM,
        double confidence,
        int bboxX,
        int bboxY,
        int bboxWidth,
        int bboxHeight) {
}
