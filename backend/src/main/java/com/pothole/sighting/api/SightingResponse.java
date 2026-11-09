package com.pothole.sighting.api;

import java.time.Instant;
import java.util.UUID;

import com.pothole.sighting.domain.Sighting;

record SightingResponse(
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
        UUID potholeId,
        String review,
        UUID reviewedBy,
        Instant reviewedAt) {

    static SightingResponse from(Sighting sighting) {
        return new SightingResponse(
                sighting.sightingId(),
                sighting.clientId(),
                sighting.capturedAt(),
                sighting.receivedAt(),
                sighting.lat(),
                sighting.lon(),
                sighting.heading(),
                sighting.speed(),
                sighting.horizontalAccuracyM(),
                sighting.confidence(),
                new int[] {sighting.bboxX(), sighting.bboxY(), sighting.bboxWidth(), sighting.bboxHeight()},
                sighting.potholeId(),
                sighting.review(),
                sighting.reviewedBy(),
                sighting.reviewedAt());
    }
}
