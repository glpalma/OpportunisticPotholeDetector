package com.pothole.dashboard.domain;

import java.time.Instant;

public record ClientStatus(String clientId, Instant lastSeenAt, Integer queueSize) {
}
