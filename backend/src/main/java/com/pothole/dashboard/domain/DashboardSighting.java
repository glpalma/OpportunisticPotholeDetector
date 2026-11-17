package com.pothole.dashboard.domain;

import java.time.Instant;
import java.util.UUID;

public record DashboardSighting(
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
        int[] bbox,
        String review,
        Instant reviewedAt,
        boolean imageAvailable) {
}
