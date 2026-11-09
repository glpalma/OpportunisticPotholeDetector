package com.pothole.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PotholeConfigurationGuardTest {

    @Test
    void blankApiKeyRefusesToStart() {
        PotholeProperties properties = new PotholeProperties();

        assertThrows(IllegalStateException.class, () -> new PotholeConfigurationGuard(properties));
    }

    @Test
    void configuredKeyStarts() {
        PotholeProperties properties = new PotholeProperties();
        properties.setApiKey("test-key");

        assertDoesNotThrow(() -> new PotholeConfigurationGuard(properties));
    }
}
