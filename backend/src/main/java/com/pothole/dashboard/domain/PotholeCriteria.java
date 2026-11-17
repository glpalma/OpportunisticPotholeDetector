package com.pothole.dashboard.domain;

import java.time.Instant;

public record PotholeCriteria(
        String status,
        Boolean neglected,
        Instant from,
        Instant to,
        Double minConfidence,
        boolean unreviewedOnly,
        boolean includeFalsePositives,
        Instant neglectCutoff,
        Double minLon,
        Double minLat,
        Double maxLon,
        Double maxLat,
        String sort,
        boolean ascending,
        int limit,
        long offset) {

    public boolean bounded() {
        return minLon != null && minLat != null && maxLon != null && maxLat != null;
    }
}
