package com.pothole.dashboard.domain;

import java.time.Instant;
import java.util.UUID;

public record PotholeSummary(
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
        UUID thumbnailSightingId) {
}
