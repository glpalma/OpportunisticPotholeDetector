package com.pothole.dashboard.domain;

import java.util.UUID;

public record MapPoint(UUID id, double lat, double lon, String status, boolean neglected) {
}
