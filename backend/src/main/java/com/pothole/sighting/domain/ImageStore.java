package com.pothole.sighting.domain;

import java.util.UUID;

public interface ImageStore {

    String finalPath(UUID sightingId);

    void writeTemp(UUID sightingId, byte[] bytes);

    void promote(UUID sightingId);

    void deleteTemp(UUID sightingId);
}
