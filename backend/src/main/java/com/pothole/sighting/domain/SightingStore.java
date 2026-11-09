package com.pothole.sighting.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SightingStore {

    double RADIUS_METERS = 10;

    List<Sighting> list(int limit, long offset);

    void lockClustering();

    boolean insertSighting(SightingMetadata metadata, Instant receivedAt, String imagePath);

    UUID attachOrCreate(SightingMetadata metadata, Instant receivedAt);

    void assignPothole(UUID sightingId, UUID potholeId);

    void enqueueThumbnail(UUID sightingId, Instant receivedAt);
}
