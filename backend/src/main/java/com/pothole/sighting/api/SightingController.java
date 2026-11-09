package com.pothole.sighting.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.pothole.sighting.domain.IngestResult;
import com.pothole.sighting.domain.InvalidSightingException;
import com.pothole.sighting.domain.SightingIngest;
import com.pothole.sighting.domain.SightingMetadata;
import com.pothole.sighting.domain.SightingStore;
import com.pothole.sighting.domain.UnsupportedImageException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
class SightingController {

    static final int DEFAULT_PAGE = 1;

    static final int DEFAULT_PAGE_SIZE = 50;

    static final int MAX_PAGE_SIZE = 100;

    private final SightingMetadataParser parser;

    private final SightingIngest ingest;

    private final SightingStore sightings;

    SightingController(SightingMetadataParser parser, SightingIngest ingest, SightingStore sightings) {
        this.parser = parser;
        this.ingest = ingest;
        this.sightings = sightings;
    }

    @GetMapping("/v1/sightings")
    ResponseEntity<List<SightingResponse>> list(
            @RequestParam(name = "page", defaultValue = "" + DEFAULT_PAGE) int page,
            @RequestParam(name = "pageSize", defaultValue = "" + DEFAULT_PAGE_SIZE) int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            return ResponseEntity.badRequest().build();
        }
        long offset = (long) (page - 1) * pageSize;
        List<SightingResponse> body = sightings.list(pageSize, offset).stream().map(SightingResponse::from).toList();
        return ResponseEntity.ok(body);
    }

    @PostMapping(path = "/v1/sightings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<Void> create(
            @RequestParam(value = "metadata", required = false) MultipartFile metadata,
            @RequestParam(value = "image", required = false) MultipartFile image) throws IOException {
        if (metadata == null || metadata.isEmpty()) {
            throw new InvalidSightingException("metadata is required");
        }
        if (image == null || image.isEmpty()) {
            throw new UnsupportedImageException("image is required");
        }
        byte[] bytes = image.getBytes();
        SightingMetadata parsed = parser.parse(new String(metadata.getBytes(), StandardCharsets.UTF_8), bytes);
        IngestResult result = ingest.ingest(parsed, bytes);
        HttpStatus status = result == IngestResult.CREATED ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).build();
    }
}
