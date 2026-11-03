# ADR-0009: Pothole clustering — position, locking, late Sightings

## Status
Accepted. Amends [ADR-0005](./0005-pothole-status.md) (the resunk rule)
and records the clustering rules from `grill-progress.md` ("POC framing").

## Context
The POC clustering rules are: spatial only, ~10 m radius, minimum support
of one Sighting, run during the upload request. Three things were left
undefined or wrong:

1. **Where a Pothole is.** Nothing said which coordinates a Pothole has.
   A running average of its Sightings drifts every time one attaches, and
   can creep along the road toward the next hole, chaining two defects
   into one.
2. **Concurrent uploads.** Clustering is "find the nearest Pothole within
   10 m, else create one". Two Sightings of the same new hole processed at
   the same time can both find nothing and create two Potholes.
3. **Late Sightings.** Under depot sync, a bus that misses a few nights
   can deliver a Sighting captured *before* its Pothole was marked
   `fixed`. ADR-0005 says any Sighting attaching to a `fixed` Pothole makes
   it `resunk`. That would record a comeback that never happened: the hole
   was simply still there and we learned about it late.

## Decision

### Position
A Pothole's `lat`/`lon` is the position of its **first Sighting** and never
changes. A Sighting attaches to the nearest Pothole whose position is within
~10 m of the Sighting's position, in any status.

### Serialization
The clustering step runs inside the upload transaction under a single
Postgres advisory lock (`pg_advisory_xact_lock`), so only one Sighting is
clustered at a time. At POC volume the lock is never contended long
enough to matter.

### Maintained fields
When a Sighting attaches, the Pothole's `sighting_count`, `max_confidence`,
`first_seen_at` (min `captured_at`) and `last_seen_at` (max `captured_at`)
are updated in the same transaction.

### `fixed_at` and late Sightings
- When the daily sweep (ADR-0010) moves a Pothole to `fixed`, it sets
  `fixed_at = last_seen_at + 30 days`: the moment the silence window
  actually ran out, not the moment the sweep happened to run.
- When a Sighting attaches to a `fixed` Pothole:
  - If its `captured_at` is **at or after `fixed_at`**, the Pothole becomes
    `resunk` (a genuine comeback, as in ADR-0005).
  - Otherwise the Sighting is a late arrival from before the Pothole was
    presumed fixed. Update `last_seen_at`. If `last_seen_at` is now less
    than 30 days ago, the Pothole goes back to `open` and `fixed_at` is
    cleared. If not, it stays `fixed` with
    `fixed_at = last_seen_at + 30 days`.
- Everything else in ADR-0005 stands.

### Review marks do not affect clustering or status
Marking a Sighting as a false positive (ADR-0014) does not detach it, does
not change the Pothole's position, counts or dates, and does not change its
status. A Pothole whose Sightings are **all** marked `false_positive` is a
false-positive Pothole; the dashboard hides those by default.

## Consequences

### Positive
- Pothole markers never move, and clusters cannot drift into each other.
- No duplicate Potholes from concurrent uploads.
- `resunk` keeps its meaning under nightly sync with missed nights.

### Negative
- The first Sighting may be the least accurate one; its GPS error becomes
  the Pothole's position forever.
- One global lock serializes all ingest. Fine for one bus, a bottleneck for
  a large fleet.
- False positives still count toward a Pothole's "last seen", so a hole
  that is only re-seen by false positives stays `open`.

### Mitigations
- `horizontal_accuracy_m` is stored on every Sighting, so position can be
  recomputed later (for example from the most accurate Sighting) by
  rebuilding membership from the Sightings table, as `grill-progress.md`
  already allows.
- The lock can become per-area (a lock keyed on a coarse grid cell) when
  ingest volume grows.
- Excluding false positives from status is a later refinement if the review
  data shows it matters.
