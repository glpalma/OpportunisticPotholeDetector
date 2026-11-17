package com.pothole.dashboard.domain;

import java.time.Instant;
import java.util.UUID;

public record ReviewCandidate(
        UUID sightingId,
        String clientId,
        Instant capturedAt,
        Instant receivedAt,
        double lat,
        double lon,
        double speed,
        double horizontalAccuracyM,
        double confidence,
        int[] bbox,
        boolean imageAvailable,
        UUID potholeId,
        double potholeLat,
        double potholeLon,
        String potholeStatus,
        boolean potholeNeglected,
        int potholeSightingCount) {
}
