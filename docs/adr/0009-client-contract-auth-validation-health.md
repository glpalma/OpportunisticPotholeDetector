# ADR-0009: Client contract — API key, validation, status codes, health check

## Status
Accepted. Amends [ADR-0003](./0003-per-sighting-multipart-upload-over-rest.md).

## Context
ADR-0003 fixed the upload shape (one Sighting = one multipart POST to
`/v1/sightings`). The POC framing in `grill-progress.md` then left the
Client contract with no authentication and no TLS requirement, on the
grounds that Clients sit on a trusted depot network.

That reasoning does not hold for the Backend: it runs on a public VPS
(see ADR-0014), so it is reachable from the whole internet no matter where
the bus connects from. "Anyone who can reach the Backend can write
Sightings" means anyone on the internet. HTTPS also costs nothing with the
reverse proxy we use, and the dashboard login requires it anyway.

ADR-0003's status-code table also had gaps: no code for a bad key, an
oversized image, or rate limiting, and `400`/`415` told the Client to keep
the Sighting forever without retrying, which fills the device's storage
with payloads that can never succeed.

## Decision

### Transport and authentication
- All Client requests use **HTTPS**. Plain HTTP is not served.
- Every `/v1/` request carries an `X-API-Key` header.
- POC: one shared key (there is one Client). When a second bus is added,
  move to one key per Client, stored as a hash next to the Client's row.

### `POST /v1/sightings` validation
The payload shape is unchanged from ADR-0003 (`metadata` JSON part +
`image` part). The Backend rejects the request when:

- `sighting_id` is not a UUID.
- `client_id` is missing or empty.
- `capturedAt` is not ISO 8601 UTC, or is more than **5 minutes in the
  future** relative to the Backend clock.
- `lat` is outside `[-90, 90]`, `lon` outside `[-180, 180]`, or the
  position is exactly `(0, 0)` (a typical "no fix" value).
- `heading` is outside `[0, 360)`; `speed` or `horizontal_accuracy_m` is
  negative.
- `confidence` is outside `[0, 1]`.
- `bbox` does not have four integers, has negative `x`/`y`, or has a
  non-positive `width`/`height`.
- `image` is missing or its bytes are not a JPEG (checked from the bytes,
  not from the declared content type).
- `image` is larger than the configured maximum. **Default 3 MB,
  configurable.** Revisit after measuring a real full frame from the
  Client camera.

### Status codes (replaces the table in ADR-0003)

| Code | Meaning | Client does |
| --- | --- | --- |
| `201` | Sighting stored | Delete the local copy |
| `200` | Same `sighting_id` already stored | Delete the local copy |
| `400` | Metadata fails validation | Drop the local copy and log it |
| `401` | Missing or wrong API key | Keep the local copy, stop the sync, log it |
| `413` | Image larger than the configured maximum | Drop the local copy and log it |
| `415` | Image missing or not a JPEG | Drop the local copy and log it |
| `429` | Rate limited | Keep the local copy, wait at least `Retry-After`, then retry |
| `5xx` / timeout | Not stored | Keep the local copy, retry with backoff |

`400`, `413` and `415` mean the payload itself is wrong and will never
succeed, so keeping it only consumes device storage. The log is how we
find out the Client is producing bad payloads.

### Health check: `GET /v1/health`
- Requires `X-API-Key`. The Client also sends `X-Client-Id` and
  `X-Queue-Size` (number of Sightings waiting on the device).
- The Backend checks the database with `SELECT 1` and answers:
  - `200` `{"service": "pothole-api", "status": "ok", "time": "<server time, ISO 8601 UTC>"}`
  - `503` `{"service": "pothole-api", "status": "db_unavailable"}`
  - `401` for a wrong key.
- It is a small dedicated controller, not a public Spring Actuator endpoint.

### Client sync loop
1. Call `GET /v1/health` with a ~5 s timeout, **without following
   redirects** (a redirect usually means a captive portal or a wrong
   network).
2. Upload only if the answer is `200` with `service == "pothole-api"`.
   `401` → stop and log. Anything else → treat as offline, retry later
   with backoff.
3. If the Client clock is more than 5 minutes ahead of the server `time`,
   hold the queue and log it (its `capturedAt` values would be rejected
   anyway).
4. Upload Sightings one at a time (or with small concurrency, per
   ADR-0003), handling each response as in the table above.

### Client registry
Every authenticated `/v1/` request, including health checks, upserts a
row in a `clients` table (`client_id`, `last_seen_at`, `queue_size`).
This is what the dashboard shows as the bus's sync status. No version
tracking and no online/offline threshold for the POC.

## Consequences

### Positive
- The public Backend no longer accepts Sightings from anyone.
- Every response tells the Client exactly what to do, and nothing stays on
  the device forever.
- The health check catches captive portals, a down database and clock
  skew before any upload is attempted.
- `queue_size` gives the dashboard a view of how much is still on the bus.

### Negative
- A shared key cannot tell Clients apart or be revoked for one bus only.
- Dropped `400`/`413`/`415` payloads are gone. Only the device log
  remembers them.

### Mitigations
- Per-Client keys are the planned upgrade when the fleet grows beyond one bus.
- The Client log should count dropped payloads by reason, the same way
  ADR-0001 counts no-fix drops.
