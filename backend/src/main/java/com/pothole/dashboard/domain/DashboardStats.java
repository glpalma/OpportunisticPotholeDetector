package com.pothole.dashboard.domain;

public record DashboardStats(
        long open,
        long neglected,
        long fixed,
        long resunk,
        long sightingsReviewed,
        long sightingsPending,
        Double detectorPrecision) {
}
