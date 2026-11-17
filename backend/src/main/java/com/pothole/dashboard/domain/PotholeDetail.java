package com.pothole.dashboard.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PotholeDetail(
        UUID id,
        double lat,
        double lon,
        String status,
        boolean neglected,
        Instant firstSeenAt,
        Instant lastSeenAt,
        int sightingCount,
        double maxConfidence,
        int reviewedCount,
        boolean falsePositive,
        List<DashboardSighting> sightings) {
}
