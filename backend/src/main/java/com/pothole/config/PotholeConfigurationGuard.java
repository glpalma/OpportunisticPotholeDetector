package com.pothole.config;

import org.springframework.stereotype.Component;

@Component
class PotholeConfigurationGuard {

    PotholeConfigurationGuard(PotholeProperties properties) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("pothole.api-key is required (set POTHOLE_API_KEY)");
        }
        if (properties.getImage().getMaxBytes() <= 0) {
            throw new IllegalStateException("pothole.image.max-bytes must be positive");
        }
        PotholeProperties.RateLimit rateLimit = properties.getRateLimit();
        if (rateLimit.getRequestsPerSecond() <= 0
                || rateLimit.getBurst() <= 0
                || rateLimit.getAuthFailuresPerMinute() <= 0) {
            throw new IllegalStateException("pothole.rate-limit values must be positive");
        }
        if (properties.getSilenceWindowDays() <= 0 || properties.getNeglectWindowDays() <= 0) {
            throw new IllegalStateException("pothole silence and neglect windows must be positive");
        }
        if (properties.getSilenceWindowDays() >= properties.getNeglectWindowDays()) {
            throw new IllegalStateException(
                    "pothole.silence-window-days must be shorter than pothole.neglect-window-days");
        }
    }
}
