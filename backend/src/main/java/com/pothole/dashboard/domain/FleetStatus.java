package com.pothole.dashboard.domain;

import java.time.Instant;
import java.util.List;

public record FleetStatus(Instant lastReceivedAt, int sightingsInLastSync, List<ClientStatus> clients) {
}
