package com.pothole.sighting.api;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.pothole.config.PotholeProperties;
import com.pothole.sighting.domain.ImageTooLargeException;
import com.pothole.sighting.domain.InvalidSightingException;
import com.pothole.sighting.domain.SightingMetadata;
import com.pothole.sighting.domain.UnsupportedImageException;

import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Component;

@Component
class SightingMetadataParser {

    private final Clock clock;

    private final long maxBytes;

    SightingMetadataParser(Clock clock, PotholeProperties properties) {
        this.clock = clock;
        this.maxBytes = properties.getImage().getMaxBytes();
    }

    SightingMetadata parse(String raw, byte[] image) {
        Map<String, Object> metadata = json(raw);
        int[] box = bbox(metadata);
        double lat = coordinate(metadata, "lat", -90, 90);
        double lon = coordinate(metadata, "lon", -180, 180);
        if (lat == 0.0d && lon == 0.0d) {
            throw new InvalidSightingException("lat,lon");
        }
        SightingMetadata parsed = new SightingMetadata(
                uuid(requiredString(metadata, "sighting_id")),
                requiredString(metadata, "client_id"),
                capturedAt(requiredString(metadata, "capturedAt")),
                lat,
                lon,
                heading(metadata),
                nonNegative(metadata, "speed"),
                nonNegative(metadata, "horizontal_accuracy_m"),
                confidence(metadata),
                box[0],
                box[1],
                box[2],
                box[3]);
        if (image == null || image.length == 0) {
            throw new UnsupportedImageException("image is required");
        }
        if (image.length > maxBytes) {
            throw new ImageTooLargeException("image is larger than " + maxBytes + " bytes");
        }
        if (!isJpeg(image)) {
            throw new UnsupportedImageException("image is not a JPEG");
        }
        return parsed;
    }

    private Map<String, Object> json(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidSightingException("metadata is required");
        }
        try {
            return JsonParserFactory.getJsonParser().parseMap(raw);
        } catch (RuntimeException ex) {
            throw new InvalidSightingException("metadata is not a JSON object");
        }
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidSightingException("sighting_id");
        }
    }

    private Instant capturedAt(String value) {
        final OffsetDateTime parsed;
        try {
            parsed = OffsetDateTime.parse(value);
        } catch (DateTimeException ex) {
            throw new InvalidSightingException("capturedAt");
        }
        if (parsed.getOffset().getTotalSeconds() != 0) {
            throw new InvalidSightingException("capturedAt");
        }
        Instant instant = parsed.toInstant();
        if (instant.isAfter(clock.instant().plus(5, ChronoUnit.MINUTES))) {
            throw new InvalidSightingException("capturedAt");
        }
        return instant;
    }

    private static double coordinate(Map<String, Object> metadata, String key, double min, double max) {
        double value = number(metadata, key);
        if (value < min || value > max) {
            throw new InvalidSightingException(key);
        }
        return value;
    }

    private static double heading(Map<String, Object> metadata) {
        double value = number(metadata, "heading");
        if (value < 0 || value >= 360) {
            throw new InvalidSightingException("heading");
        }
        return value;
    }

    private static double nonNegative(Map<String, Object> metadata, String key) {
        double value = number(metadata, key);
        if (value < 0) {
            throw new InvalidSightingException(key);
        }
        return value;
    }

    private static double confidence(Map<String, Object> metadata) {
        double value = number(metadata, "confidence");
        if (value < 0 || value > 1) {
            throw new InvalidSightingException("confidence");
        }
        return value;
    }

    private static int[] bbox(Map<String, Object> metadata) {
        Object value = metadata.get("bbox");
        if (!(value instanceof List<?> list) || list.size() != 4) {
            throw new InvalidSightingException("bbox");
        }
        int x = integral(list.get(0), "bbox");
        int y = integral(list.get(1), "bbox");
        int width = integral(list.get(2), "bbox");
        int height = integral(list.get(3), "bbox");
        if (x < 0 || y < 0 || width <= 0 || height <= 0) {
            throw new InvalidSightingException("bbox");
        }
        return new int[] {x, y, width, height};
    }

    private static String requiredString(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new InvalidSightingException(key);
        }
        return text;
    }

    private static double number(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (!(value instanceof Number number)) {
            throw new InvalidSightingException(key);
        }
        double parsed = number.doubleValue();
        if (!Double.isFinite(parsed)) {
            throw new InvalidSightingException(key);
        }
        return parsed;
    }

    private static int integral(Object value, String key) {
        if (!(value instanceof Number number)) {
            throw new InvalidSightingException(key);
        }
        double parsed = number.doubleValue();
        if (!Double.isFinite(parsed) || parsed != Math.rint(parsed) || parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
            throw new InvalidSightingException(key);
        }
        return number.intValue();
    }

    private static boolean isJpeg(byte[] image) {
        return image.length >= 3
                && (image[0] & 0xFF) == 0xFF
                && (image[1] & 0xFF) == 0xD8
                && (image[2] & 0xFF) == 0xFF;
    }
}
