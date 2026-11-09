# ADR-0011: Pothole clustering — position and locking

## Status
Accepted. Records the clustering rules from `grill-progress.md` ("POC framing"). Does not change the resunk rule in [ADR-0005](./0005-pothole-status.md): any Sighting that attaches to a `fixed` Pothole marks it `resunk`, including a Sighting that was captured earlier and uploaded late.

## Context
The POC clustering rules are: spatial only, ~10 m radius, minimum support of one Sighting, run during the upload request. Two things were left undefined:

1. **Where a Pothole is.** Nothing said which coordinates a Pothole has. A running average of its Sightings drifts every time one attaches, and can creep along the road toward the next hole, chaining two defects into one.
2. **Concurrent uploads.** Clustering is "find the nearest Pothole within 10 m, else create one". Two Sightings of the same new hole processed at the same time can both find nothing and create two Potholes.

A late upload is not a third rule. [ADR-0005](./0005-pothole-status.md) already says that a Sighting attaching to a `fixed` Pothole makes it `resunk` inside that upload request. Comparing `capturedAt` with the moment the silence window ran out, and reopening the Pothole instead, is not the rule.

## Decision

### Position
A Pothole's `lat`/`lon` is the position of its **first Sighting** and never changes. A Sighting attaches to the nearest Pothole whose position is within ~10 m of the Sighting's position, in any status.

### Serialization
The clustering step runs inside the upload transaction under a single Postgres advisory lock (`pg_advisory_xact_lock`), so only one Sighting is clustered at a time. At POC volume the lock is never contended long enough to matter.

### Maintained fields
When a Sighting attaches, the Pothole's `sighting_count`, `max_confidence`, `first_seen_at` (min `captured_at`) and `last_seen_at` (max `captured_at`) are updated in the same transaction.

### Status on attach
Follow [ADR-0005](./0005-pothole-status.md). A Sighting that attaches to a `fixed` Pothole sets the status to `resunk` in that same request. A Sighting that attaches to a `resunk` Pothole leaves the status `resunk`. There is no reopen path for a late upload.

### Review marks do not affect clustering or status
Marking a Sighting as a false positive ([ADR-0016](./0016-dashboard-design.md)) does not detach it, does not change the Pothole's position, counts or dates, and does not change its status. A Pothole whose Sightings are **all** marked `false_positive` is a false-positive Pothole; the dashboard hides those by default.

## Consequences

### Positive
- Pothole markers never move, and clusters cannot drift into each other.
- No duplicate Potholes from concurrent uploads.
- Resunk stays the simple fact from ADR-0005: it was `fixed`, and a Sighting attached.

### Negative
- The first Sighting may be the least accurate one; its GPS error becomes the Pothole's position forever.
- One global lock serializes all ingest. Fine for one bus, a bottleneck for a large fleet.
- A late upload of a Sighting captured before the silence window ran out still counts as a comeback.
- False positives still count toward a Pothole's "last seen", so a hole that is only re-seen by false positives stays `open`.

### Mitigations
- `horizontal_accuracy_m` is stored on every Sighting, so position can be recomputed later (for example from the most accurate Sighting) by rebuilding membership from the Sightings table, as `grill-progress.md` already allows.
- The lock can become per-area (a lock keyed on a coarse grid cell) when ingest volume grows.
- Excluding false positives from status is a later refinement if the review data shows it matters.
