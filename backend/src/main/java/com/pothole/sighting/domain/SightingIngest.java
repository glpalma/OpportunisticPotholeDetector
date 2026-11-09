package com.pothole.sighting.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.pothole.client.domain.ClientRegistry;

import org.springframework.stereotype.Service;

@Service
public class SightingIngest {

    private final Clock clock;

    private final ClientRegistry clients;

    private final SightingStore sightings;

    private final ImageStore images;

    private final IngestTransaction transactions;

    public SightingIngest(Clock clock, ClientRegistry clients, SightingStore sightings, ImageStore images,
            IngestTransaction transactions) {
        this.clock = clock;
        this.clients = clients;
        this.sightings = sightings;
        this.images = images;
        this.transactions = transactions;
    }

    public IngestResult ingest(SightingMetadata metadata, byte[] image) {
        images.writeTemp(metadata.sightingId(), image);
        try {
            return transactions.execute(() -> store(metadata));
        } finally {
            try {
                images.deleteTemp(metadata.sightingId());
            } catch (RuntimeException ex) {
                // The row is already committed. A leftover part file is removed by the temp sweep.
            }
        }
    }

    private IngestResult store(SightingMetadata metadata) {
        Instant receivedAt = clock.instant();
        clients.upsert(metadata.clientId(), receivedAt, null);
        boolean inserted = sightings.insertSighting(metadata, receivedAt, images.finalPath(metadata.sightingId()));
        if (!inserted) {
            return IngestResult.DUPLICATE;
        }
        sightings.lockClustering();
        UUID potholeId = sightings.attachOrCreate(metadata, receivedAt);
        sightings.assignPothole(metadata.sightingId(), potholeId);
        sightings.enqueueThumbnail(metadata.sightingId(), receivedAt);
        images.promote(metadata.sightingId());
        return IngestResult.CREATED;
    }
}
