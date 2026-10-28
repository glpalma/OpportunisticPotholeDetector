# ADR-0002: Nightly depot sync, full-frame images per Sighting

## Status
Accepted

## Context
Clients are mounted on buses. Buses have no mobile data plan and no cellular
connectivity while in service. The only time a Client has network access is
overnight at the Depot, over the Depot's Wi-Fi.

This inverts the normal "spotty mobile" problem: we are not drip-feeding
small payloads over a flaky link. We are doing one big transfer per Client
per day, over a known good link, on a known schedule.

Options considered for image payload per Sighting:

1. **No image.** Pure metadata (lat/lon/confidence).
2. **Thumbnail only** (~20 KB).
3. **Thumbnail + on-device full-frame ring buffer, pulled on demand.**
4. **Full frame always** (~200–500 KB per Sighting).

Under the "spotty mobile" assumption, option 3 was the obvious answer.
Under the "nightly depot Wi-Fi" assumption, bandwidth per Sighting is
essentially free, so the trade-off collapses.

## Decision
Option 4. Every Sighting carries its full-resolution representative frame
(the highest-confidence frame of the collapsed burst).

## Consequences

### Positive
- No separate "image fetch" protocol. One upload path, no on-demand pulls,
  no backend-initiated requests back into the Client.
- Full-fidelity data for human verification, false-positive auditing, and
  model retraining from day one, with no gatekeeping decision about which
  frames are "worth" fetching.
- The Backend sees exactly what the model saw.

### Negative
- On-device storage scales with Sighting count. Order-of-magnitude: 500
  Sightings/day × 500 KB ≈ 250 MB/day. One week unsynced ≈ 1.75 GB. SD card
  sized accordingly (and local retention policy needed — see ADR-TBD on
  on-device retention).
- Depot sync window must comfortably fit a full day's worth of uploads.
  Order-of-magnitude: 250 MB over depot Wi-Fi is minutes, not hours — but
  "the Pi has power and Wi-Fi at the depot" is a real-world assumption
  that needs confirming (see pending question on depot power/sync window).
- No fallback if the depot link is down for several consecutive nights:
  the on-device cache has to absorb it or we start dropping Sightings.

### Mitigations
- Local retention policy will cap total on-device bytes; oldest Sightings
  dropped first with a visible local metric.
- Sync protocol must be resumable at Sighting granularity, so a partial
  night doesn't require re-uploading what already landed.
