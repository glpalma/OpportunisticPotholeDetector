package com.pothole.sighting.api;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.pothole.config.PotholeProperties;
import com.pothole.sighting.domain.ImageTooLargeException;
import com.pothole.sighting.domain.InvalidSightingException;
import com.pothole.sighting.domain.SightingMetadata;
import com.pothole.sighting.domain.UnsupportedImageException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SightingMetadataParserTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), ZoneOffset.UTC);

    private static final byte[] JPEG = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};

    private final SightingMetadataParser parser = parser(1024);

    @Test
    void acceptsAValidSighting() {
        SightingMetadata metadata = parser.parse(json(
                "3f1c2c2e-0b1a-4d5e-9f70-1a2b3c4d5e6f",
                "bus-1",
                "2026-11-09T14:00:00Z",
                "-23.55",
                "-46.63",
                "90",
                "8.5",
                "4",
                "0.91",
                "[10,20,30,40]"), JPEG);

        assertEquals("bus-1", metadata.clientId());
        assertEquals(10, metadata.bboxX());
        assertEquals(40, metadata.bboxHeight());
    }

    @Test
    void capturedAtExactlyFiveMinutesAheadIsAllowed() {
        parser.parse(json(
                "3f1c2c2e-0b1a-4d5e-9f70-1a2b3c4d5e6f",
                "bus-1",
                "2026-11-09T15:05:00Z",
                "-23.55",
                "-46.63",
                "0",
                "0",
                "0",
                "1",
                "[0,0,1,1]"), JPEG);
    }

    @Test
    void rejectsMetadataThatCanNeverSucceed() {
        assertInvalid(json("not-a-uuid", "bus-1", "2026-11-09T14:00:00Z", "-23.55", "-46.63", "90", "1", "1", "0.5", "[1,2,3,4]"));
        assertInvalid(json("3f1c2c2e-0b1a-4d5e-9f70-1a2b3c4d5e6f", "  ", "2026-11-09T14:00:00Z", "-23.55", "-46.63", "90", "1", "1", "0.5", "[1,2,3,4]"));
        assertInvalid(json("3f1c2c2e-0b1a-4d5e-9f70-1a2b3c4d5e6f", "bus-1", "2026-11-09T14:00:00-03:00", "-23.55", "-46.63", "90", "1", "1", "0.5", "[1,2,3,4]"));
        assertInvalid(json("3f1c2c2e-0b1a-4d5e-9f70-1a2b3c4d5e6f", "bus-1", "2026-11-09T15:06:00Z", "-23.55", "-46.63", "90", "1", "1", "0.5", "[1,2,3,4]"));
        assertInvalid(json("3f1c2c2e-0b1a-4d5e-9f70-1a2b3c4d5e6f", "bus-1", "2026-11-09T14:00:00Z", "91", "-46.63", "90", "1", "1", "0.5", "[1,2,3,4]"));
        assertInvalid(json("3f1c2c2e-0b1a-4d5e-9f70-1a2b3c4d5e6f", "bus-1", "2026-11-09T14:00:00Z", "-23.55", "181", "90", "1", "1", "0.5", "[1,2,3,4]"));
        assertInvalid(json("3f1c2c2e-0b1a-4d5e-9f70-1a2b3c4d5e6f", "bus-1", "2026-11-09T14:00:00Z", "0", "0", "90", "1", "1", "0.5", "[1,2,3,4]"));
        assertInvalid(json("3f1c2c2e-0b1a-4d5e-9f70-1a2b3c4d5e6f", "bus-1", "2026-11-09T14:00:00Z", "-23.55", "-46.63", "360", "1", "1", "0.5", "[1,2,3,4]"));
        assertInvalid(json("3f1c2c2e-0b1a-4d5e-9f70-1a2b3c4d5e6f", "bus-1", "2026-11-09T14:00:00Z", "-23.55", "-46.63", "-1", "1", "1", "0.5", "[1,2,3,4]"));
        assertInvalid(json("3f1c2c2e-0b1a-4d5e-9f70-1a2b3c4d5e6f", "bus-1", "2026-11-09T14:00:00Z", "-23.55", "-46.63", "90", "-0.1", "1", "0.5", "[1,2,3,4]"));
        assertInvalid(json("3f1c2c2e-0b1a-4d5e-9f70-1a2b3c4d5e6f", "bus-1", "2026-11-09T14:00:00Z", "-23.55", "-46.63", "90", "1", "-1", "0.5", "[1,2,3,4]"));
        assertInvalid(json("3f1c2c2e-0b1a-4d5e-9f70-1a2b3c4d5e6f", "bus-1", "2026-11-09T14:00:00Z", "-23.55", "-46.63", "90", "1", "1", "1.1", "[1,2,3,4]"));
        assertInvalid(json("3f1c2c2e-0b1a-4d5e-9f70-1a2b3c4d5e6f", "bus-1", "2026-11-09T14:00:00Z", "-23.55", "-46.63", "90", "1", "1", "0.5", "[1,2,3]"));
        assertInvalid(json("3f1c2c2e-0b1a-4d5e-9f70-1a2b3c4d5e6f", "bus-1", "2026-11-09T14:00:00Z", "-23.55", "-46.63", "90", "1", "1", "0.5", "[-1,2,3,4]"));
        assertInvalid(json("3f1c2c2e-0b1a-4d5e-9f70-1a2b3c4d5e6f", "bus-1", "2026-11-09T14:00:00Z", "-23.55", "-46.63", "90", "1", "1", "0.5", "[1,2,0,4]"));
        assertInvalid(json("3f1c2c2e-0b1a-4d5e-9f70-1a2b3c4d5e6f", "bus-1", "2026-11-09T14:00:00Z", "-23.55", "-46.63", "90", "1", "1", "0.5", "[1,2,3.5,4]"));
        assertInvalid("{");
    }

    @Test
    void rejectsAMissingOrNonJpegImage() {
        String valid = json("3f1c2c2e-0b1a-4d5e-9f70-1a2b3c4d5e6f", "bus-1", "2026-11-09T14:00:00Z", "-23.55", "-46.63", "90", "1", "1", "0.5", "[1,2,3,4]");

        assertThrows(UnsupportedImageException.class, () -> parser.parse(valid, new byte[0]));
        assertThrows(UnsupportedImageException.class, () -> parser.parse(valid, new byte[] {1, 2, 3, 4}));
    }

    @Test
    void rejectsAnImageOverTheConfiguredMaximum() {
        String valid = json("3f1c2c2e-0b1a-4d5e-9f70-1a2b3c4d5e6f", "bus-1", "2026-11-09T14:00:00Z", "-23.55", "-46.63", "90", "1", "1", "0.5", "[1,2,3,4]");
        byte[] oversized = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0};

        assertThrows(ImageTooLargeException.class, () -> parser(8).parse(valid, oversized));
    }

    private static void assertInvalid(String raw) {
        SightingMetadataParser parser = parser(1024);
        assertThrows(InvalidSightingException.class, () -> parser.parse(raw, JPEG));
    }

    private static SightingMetadataParser parser(long maxBytes) {
        PotholeProperties properties = new PotholeProperties();
        properties.getImage().setMaxBytes(maxBytes);
        return new SightingMetadataParser(CLOCK, properties);
    }

    private static String json(String sightingId, String clientId, String capturedAt, String lat, String lon,
            String heading, String speed, String accuracy, String confidence, String bbox) {
        return """
                {"sighting_id":"%s","client_id":"%s","capturedAt":"%s","lat":%s,"lon":%s,"heading":%s,"speed":%s,"horizontal_accuracy_m":%s,"confidence":%s,"bbox":%s}
                """.formatted(sightingId, clientId, capturedAt, lat, lon, heading, speed, accuracy, confidence, bbox);
    }
}
