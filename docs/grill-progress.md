# Grill-with-docs progress

Running record of the design-grilling session. Resume by reading this top-to-bottom.

## Decided (and documented)

See [`CONTEXT.md`](../CONTEXT.md) for the glossary and [`docs/adr/`](./adr/)
for architectural decisions. Short summary of what's locked in:

1. **Deployment framing** — bus fleet, one Client (rk322x TV box + camera)
   per bus. A Client is not assumed to have a network while in service.
   Whenever it connects, it syncs; night is not a special window. Depot
   Wi-Fi is the expected network, and depot power and Wi-Fi are stipulated
   by the fleet operator. The POC starts with **one bus**.
   See [ADR-0006](./adr/0006-client-hardware-rk322x-tv-box.md) and
   [ADR-0002](./adr/0002-sync-on-connect-with-full-frames.md).
2. **Core terms: Sighting vs Pothole.**
   - **Sighting** = one vehicle pass by one suspected pothole. Client-produced,
     dedup'd on-device across contiguous frames. "I saw it once."
   - **Pothole** = backend-derived cluster of Sightings across Clients and time.
     The dashboard shows Potholes, not raw Sightings. A Sighting is opened
     to confirm it or mark it a false positive.
   - **Review** = a team member's verdict on a Sighting (pothole / false positive).
   - Term "Occurrence" retired.
3. **GPS is mandatory per Sighting.** Fields: `lat`, `lon`, GPS `timestamp`
   (not Client wall-clock), `heading`, `speed`, `horizontal_accuracy_m`.
   No-fix detections are dropped on-device, not buffered or interpolated.
   See [ADR-0001](./adr/0001-drop-sightings-without-gps-fix.md).
4. **Full-resolution frame per Sighting, and it is required.** The Client
   syncs whenever it has a network. The Client sends no thumbnails and
   there are no on-demand pulls. The Backend derives display thumbnails.
   See [ADR-0002](./adr/0002-sync-on-connect-with-full-frames.md)
   and [ADR-0010](./adr/0010-background-jobs-and-thumbnails.md).
5. **Sighting payload** = `sighting_id` (Client-generated UUID), `client_id`,
   GPS fields, `confidence`, `bbox` (`[x, y, width, height]` in pixels of
   the representative frame, origin top-left), `image` (required full frame JPEG).
   **No class, no severity emitted by the Client** — the frame preserves
   all info for server-side re-derivation later.
6. **Sync protocol: one Sighting per request.** `multipart/form-data` POST
   to `/v1/sightings` over HTTPS, with an `X-API-Key`. The Backend does not
   accept a batch. The Client contract is versioned;
   dashboard reads are not part of it. No batching, no object storage, no
   split metadata/image shape. Idempotent via Client-generated `sighting_id`.
   Status codes: `201`/`200` delete locally; `400`/`413`/`415` drop and log;
   `401` stop; `429` wait `Retry-After`; `5xx` retry. A `GET /v1/health`
   check runs before each sync.
   See [ADR-0003](./adr/0003-per-sighting-multipart-upload-over-rest.md) and
   [ADR-0007](./adr/0007-client-contract-auth-validation-health.md).
7. **Stacks.** Backend is Spring Boot with **Postgres** (no PostGIS) and
   Flyway. Dashboard is Angular with Angular Material and **Leaflet**. The
   on-device Client stays a separate program. This repo houses all three in
   `backend/`, `frontend/`, and `client/`.
   See [ADR-0004](./adr/0004-spring-boot-and-angular.md),
   [ADR-0008](./adr/0008-postgres-schema-and-image-storage.md) and
   [ADR-0014](./adr/0014-dashboard-design.md).
8. **Pothole status:** stored `open`, `fixed`, or `resunk`. Neglected is
   derived (open, and the first `capturedAt` is 30 days ago or older).
   Silence → fixed uses `capturedAt` and a 30-day window. Fixed → resunk
   happens when any later Sighting attaches, including a late upload of an
   older Sighting. Rows are not deleted by age.
   See [ADR-0005](./adr/0005-pothole-status.md) and
   [ADR-0009](./adr/0009-pothole-clustering-position-and-locking.md).
9. **Clustering details:** a Pothole's position is its first Sighting's and
   never moves; clustering runs under one advisory lock; counts and dates
   are maintained on the Pothole at ingest. Clustering does not change the
   resunk rule.
   See [ADR-0009](./adr/0009-pothole-clustering-position-and-locking.md).
10. **Background work:** `jobs` table in Postgres polled with `SKIP LOCKED`
    (thumbnails), plus scheduled sweeps (temp files, daily status, daily
    retention purge). See [ADR-0010](./adr/0010-background-jobs-and-thumbnails.md).
11. **Privacy and retention:** LGPD research basis (to be confirmed), camera
    angled at the road, images only behind login, image files purged after
    6 months (configurable) while rows are kept.
    See [ADR-0011](./adr/0011-privacy-and-photo-retention.md).
12. **Hosting:** one DigitalOcean droplet with Docker Compose (Caddy, Spring
    Boot, Postgres), Bucket4j rate limiting, weekly backups, k6 stress test.
    See [ADR-0012](./adr/0012-hosting-and-operations.md).
13. **Dashboard access:** hand-created user accounts, Spring Security JSON
    login, sessions in Postgres, CSRF on, same origin as the API.
    See [ADR-0013](./adr/0013-dashboard-accounts-and-sessions.md).
14. **Dashboard design:** pt-BR UI. Map and table show Potholes, not raw
    Sightings. Pothole detail and the review queue exist so a Sighting can
    be confirmed or marked a false positive. Also: bus status card, stats
    card with detector precision, CSV export of Sightings. False-positive
    reviews do not change status; all-false-positive Potholes are hidden
    by default.
    See [ADR-0014](./adr/0014-dashboard-design.md).

## Paused mid-question

No question is currently paused.

## POC framing

The user has clarified this is a **proof of concept**, not a production
deployment. Decisions below are sized accordingly:

- **Tenancy**: single-tenant. No `tenant_id` anywhere.
- **Scale**: one bus, one Client.
- **Client auth**: one shared API key over HTTPS (ADR-0007). The earlier
  "no auth, no TLS" shortcut was revised: the Backend is a public VPS, so
  "anyone who can reach the Backend" meant anyone on the internet.
  (ponytail: shared key; ceiling is "a leaked key lets anyone write
  Sightings, and one bus can't be revoked alone"; upgrade path is
  key-per-Client, then mTLS.)
- **Dashboard auth**: user accounts and sessions (ADR-0013).
- **Backend storage schema**: see ADR-0008.
- **Pothole clustering**:
  - Spatial-only, ~10m radius. No heading-awareness, no time-awareness.
  - Minimum support = 1 Sighting (every detection becomes a Pothole; POC
    wants to see raw model output).
  - Runs on-ingest: each POST handler does one spatial query, attaches to
    nearest Pothole within 10m or creates a new one, under one advisory
    lock (ADR-0009).
  - Status and aging: see ADR-0005 and ADR-0009. Clustering itself stays
    reversible by rebuilding membership from the Sightings table. Resunk is
    not reversible that way; it has to have been recorded when the Sighting
    arrived.
  - (ponytail: spatial-only; ceilings are "opposite-lane potholes merge"
    and "a route change looks like a repair"; upgrade path is
    spatial+heading, then a record of passes that detected nothing.)

## Queued branches (rough priority order)

Ordered by how much downstream work each unlocks.

1. ~~**Client identity / auth.**~~ Shared API key over HTTPS for the POC.
   See [ADR-0007](./adr/0007-client-contract-auth-validation-health.md).
2. ~~**Pothole clustering strategy.**~~ Resolved for POC. See "POC framing"
   above and [ADR-0009](./adr/0009-pothole-clustering-position-and-locking.md).
3. **On-device dedup specifics.** The "collapse contiguous frames into one
   Sighting" logic. Time/distance window, confidence oscillation handling,
   which frame becomes the representative. Depends on the frame rate the
   rk322x achieves (branch 4). Not grilled yet.
4. **Detector model.** What model, sourced from where, trained on what,
   updateable how. Also: on-device inference stack on the rk322x (no NPU).
   Assumed feasible for now; **benchmark before building the Client**
   (ADR-0006). Not grilled yet.
5. ~~**Dashboard scope.**~~ Resolved in [ADR-0014](./adr/0014-dashboard-design.md).
   Severity view dropped (nothing derives severity yet). False-positive
   feedback is collected as Reviews; feeding them back into training is
   part of branch 4.
6. **On-device retention policy.** Local DB size cap, image ring buffer,
   drop-oldest-first behavior, local observability (how does an operator
   know a Client is running out of disk). The rk322x eMMC is often
   8–16 GB. Not grilled yet.
7. ~~**Backend tech stack.**~~ Spring Boot + Postgres (ADR-0008).
8. ~~**Frontend tech stack.**~~ Angular + Angular Material + Leaflet (ADR-0014).
9. **Privacy confirmation.** Confirm the LGPD legal basis and who is data
   controller (university vs fleet operator) with the university's DPO /
   advisor before collecting on real routes (ADR-0011).
10. **Retraining vs retention.** The 6-month image purge (ADR-0011)
    removes frames that ADR-0002 keeps for retraining. Decide whether
    selected, blurred frames are copied to a training set before the purge.
11. **Measurements before tuning.** Real full-frame size (sets the upload
    limit, default 3 MB, ADR-0007) and real daily volume (sets disk and
    retention, ADR-0012).
12. **Quiet `resunk` Potholes.** ADR-0005 does not say whether a `resunk`
    Pothole that goes quiet for 30 days moves back to `fixed`. The daily
    sweep currently only moves `open` Potholes (ADR-0010).

## How this was built

Via the [`grill-with-docs`](~/.agents/skills/grill-with-docs/SKILL.md) skill:
one question at a time, with a recommended answer, documentation (`CONTEXT.md`
and ADRs) updated inline as decisions crystallize. The 2026-11-02 update
reconciled these docs with backend and dashboard decisions made in separate
design sessions (ADR-0006 to ADR-0014). The 2026-11-09 update recorded
the follow-up decisions: rk322x stands; resunk stays "any attach to a
fixed Pothole"; the dashboard shows Potholes plus false-positive review
of Sightings; the full frame is required; one Sighting per request; sync
runs on every network connection, not only at night.
