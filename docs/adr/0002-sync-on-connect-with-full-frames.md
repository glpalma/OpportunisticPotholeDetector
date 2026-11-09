# ADR-0002: Sync on connect, full frame required per Sighting

## Status
Accepted. "Nightly depot sync" was the wrong description: the Client syncs whenever it has a network, not only overnight. Amended by [ADR-0010](./0010-background-jobs-and-thumbnails.md) ("no thumbnails" applies to the Client; the Backend derives display thumbnails) and [ADR-0011](./0011-privacy-and-photo-retention.md) (image retention). The Client is the rk322x in [ADR-0006](./0006-client-hardware-rk322x-tv-box.md).

## Context
Clients are mounted on buses. A Client is not assumed to have a network while in service. When a network appears, it uploads the Sightings it has not yet sent. Depot Wi-Fi is the expected network, and that connection can happen at any time of day. Night is not a special sync window.

This is still not a "spotty mobile" design. There is no cellular plan, and we are not drip-feeding tiny payloads. A connection may be a long stay at the depot or a shorter one. The Client should upload as far as that link allows and continue the next time a network appears.

Options considered for the image on a Sighting:

1. **No image.** Pure metadata (lat/lon/confidence).
2. **Thumbnail only** (~20 KB).
3. **Thumbnail + on-device full-frame ring buffer, pulled on demand.**
4. **Full frame always** (~200–500 KB per Sighting).

Under a metered mobile link, option 3 was the obvious answer. Here the link is Wi-Fi when it exists, and the frame is what a person later uses to mark a false positive. The trade-off collapses toward the full frame.

## Decision
Option 4. Every Sighting carries its full-resolution representative frame (the highest-confidence frame of the collapsed burst).

The frame is required. The Backend does not store a Sighting whose image is missing or is not a JPEG (see [ADR-0003](./0003-per-sighting-multipart-upload-over-rest.md) and [ADR-0007](./0007-client-contract-auth-validation-health.md)).

The Client tries to sync every time it connects to a network. Sync is not scheduled for night.

## Consequences

### Positive
- No separate "image fetch" protocol. One upload path, no on-demand pulls, no backend-initiated requests back into the Client.
- Full-fidelity data for human verification, false-positive review, and model retraining from day one.
- The Backend sees exactly what the model saw.
- A short connection still makes progress: each request is one Sighting ([ADR-0003](./0003-per-sighting-multipart-upload-over-rest.md)).

### Negative
- On-device storage scales with Sighting count. Order-of-magnitude: 500 Sightings/day × 500 KB ≈ 250 MB/day. The rk322x eMMC is often 8–16 GB ([ADR-0006](./0006-client-hardware-rk322x-tv-box.md)), so several days without a network fill it. Local retention is still open.
- A connection that drops mid-sync has to resume at the next unsynced Sighting.
- Days with no network at all: the on-device cache absorbs them, or the Client starts dropping Sightings.

### Mitigations
- Local retention policy will cap total on-device bytes; oldest Sightings dropped first with a visible local metric.
- Sync is resumable at Sighting granularity, so a partial connection does not re-upload what already landed.
