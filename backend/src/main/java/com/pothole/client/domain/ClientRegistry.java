package com.pothole.client.domain;

import java.time.Instant;

public interface ClientRegistry {

    void upsert(String clientId, Instant lastSeenAt, Integer queueSize);
}
