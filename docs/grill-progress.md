# Grill-with-docs progress

Running record of the design-grilling session. Resume by reading this top-to-bottom.

## Decided (and documented)

See [`CONTEXT.md`](../CONTEXT.md) for the glossary and [`docs/adr/`](./adr/)
for architectural decisions. Short summary of what's locked in:

1. **Deployment framing** — bus fleet, Pi+camera per bus, no connectivity in
   service, nightly sync at the depot over Wi-Fi. Depot power and Wi-Fi are
   stipulated by the fleet operator as a given (long sync window, hours).
2. **Core terms: Sighting vs Pothole.**
   - **Sighting** = one vehicle pass by one suspected pothole. Client-produced,
     dedup'd on-device across contiguous frames. "I saw it once."
   - **Pothole** = backend-derived cluster of Sightings across Clients and time.
     The dashboard shows Potholes, not raw Sightings.
   - Term "Occurrence" retired.
3. **GPS is mandatory per Sighting.** Fields: `lat`, `lon`, GPS `timestamp`
   (not Pi wall-clock), `heading`, `speed`, `horizontal_accuracy_m`.
   No-fix detections are dropped on-device, not buffered or interpolated.
   See [ADR-0001](./adr/0001-drop-sightings-without-gps-fix.md).
4. **Full-resolution frame per Sighting**, synced nightly. No thumbnails, no
   on-demand pulls. Bandwidth-per-Sighting ceases to matter under nightly
   depot sync. See [ADR-0002](./adr/0002-nightly-depot-sync-with-full-frames.md).
5. **Sighting payload** = `sighting_id` (Client-generated UUID), `client_id`,
   GPS fields, `confidence`, `bbox` (`[x, y, width, height]` in pixels of
   the representative frame, origin top-left), `image` (full frame JPEG).
   **No class, no severity emitted by the Client** — the frame preserves
   all info for server-side re-derivation later.
6. **Sync protocol: per-Sighting `multipart/form-data` POST to `/v1/sightings`
   over REST/HTTPS.** The Client contract is versioned; dashboard reads are
   not part of it. No batching, no object storage, no split metadata/image
   shape. Idempotent via Client-generated `sighting_id`. Status codes on
   that route: `201` created, `200` duplicate, `400` bad metadata, `415`
   not a JPEG, `5xx` retry. Client deletes locally only on `200` or `201`.
   The earlier "batched REST" idea was retired — it was imported from
   spotty-mobile thinking and doesn't survive the depot-Wi-Fi setting.
   See [ADR-0003](./adr/0003-per-sighting-multipart-upload-over-rest.md).
7. **Stacks.** Backend is Spring Boot. Dashboard is Angular. The on-device
   Client stays a separate Pi program. This repo houses all three in
   `backend/`, `frontend/`, and `client/`. Database and map library are
   still open. Chosen because the team already works in this stack,
   accepting a heavier POC than FastAPI.
   See [ADR-0004](./adr/0004-spring-boot-and-angular.md).
8. **Pothole status:** stored `open`, `fixed`, or `resunk`. Neglected is
   derived (open, and the first `capturedAt` is past the neglect window).
   Silence → fixed uses `capturedAt` and the silence window. Fixed → resunk
   happens when a new Sighting attaches. Neglected means `open` and the
   first `capturedAt` is past the neglect window. Rows are not deleted by age.
   See [ADR-0005](./adr/0005-pothole-status.md).
9. **Window lengths:** silence and neglect are separate durations, read
   from configuration. Defaults are 14 days and 30 days. The silence
   window is shorter so an on-time repair is not labeled neglected while
   it waits to go fixed.
   See [ADR-0006](./adr/0006-silence-and-neglect-windows.md).
10. **Connection check:** `GET /health` returns `200` and `{"status":"up"}`
    when the process is serving HTTP. Unversioned, no body, does not check
    the database. Any other response means the Client is not talking to the
    Backend.
    See [ADR-0007](./adr/0007-health-probe.md).

## Paused mid-question

No question is currently paused.

## POC framing

The user has clarified this is a **proof of concept**, not a production
deployment. Decisions below are sized accordingly:

- **Tenancy**: single-tenant. No `tenant_id` anywhere.
- **Auth**: none for now. Backend trusts `client_id` from the payload.
  No TLS requirement, no API keys, no mTLS. This is a deliberate POC
  shortcut, not a security posture. Must be revisited before any field
  deployment with real buses on real roads.
  (ponytail: no auth; ceiling is "anyone who can reach the Backend can
  write Sightings"; upgrade path is bearer-key-per-Client, then mTLS.)
- **Backend storage schema (minimum)**: `sighting_id`, `client_id`,
  `capturedAt`, GPS fields (lat, lon, heading, speed, horizontal_accuracy_m),
  `confidence`, `bbox`, `image`, `receivedAt`. See `CONTEXT.md` for term meanings.
- **Pothole clustering**:
  - Spatial-only, ~10m radius. No heading-awareness, no time-awareness.
  - Minimum support = 1 Sighting (every detection becomes a Pothole; POC
    wants to see raw model output).
  - Runs on-ingest: each POST handler does one spatial query, attaches to
    nearest Pothole within 10m or creates a new one.
  - Status and aging: see ADR-0005. Clustering itself stays reversible by
    rebuilding membership from the Sightings table. Resunk is not
    reversible that way; it has to have been recorded when the Sighting
    arrived.
  - (ponytail: spatial-only; ceilings are "opposite-lane potholes merge"
    and "a route change looks like a repair"; upgrade path is
    spatial+heading, then a record of passes that detected nothing.)

## Queued branches (rough priority order)

Ordered by how much downstream work each unlocks.

1. ~~**Client identity / auth.**~~ Deferred for POC. See "POC framing" above.
2. ~~**Pothole clustering strategy.**~~ Resolved for POC. See "POC framing" above.
3. **On-device dedup specifics.** The "collapse contiguous frames into one
   Sighting" logic. Time/distance window, confidence oscillation handling,
   which frame becomes the representative. Not grilled yet.
4. **Detector model.** What model, sourced from where, trained on what,
   updateable how. Also: on-device inference stack (ONNX? TFLite? Coral?).
   Not grilled yet — POC literally doesn't run without picking one.
5. **Dashboard scope.** Beyond "map with dots" — filters, severity view
   (needs server-side severity derivation), export, false-positive feedback
   loop back into training. Not grilled yet.
6. **On-device retention policy.** Local DB size cap, image ring buffer,
   drop-oldest-first behavior, local observability (how does an operator
   know a Client is running out of disk). Not grilled yet.
7. ~~**Backend tech stack.**~~ Spring Boot. Database still open.
8. ~~**Frontend tech stack.**~~ Angular. Map library still open.

## How this was built

Via the [`grill-with-docs`](~/.agents/skills/grill-with-docs/SKILL.md) skill:
one question at a time, with a recommended answer, documentation (`CONTEXT.md`
and ADRs) updated inline as decisions crystallize.
