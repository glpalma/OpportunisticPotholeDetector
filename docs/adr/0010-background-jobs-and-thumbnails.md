# ADR-0010: Background jobs in Postgres, server-side thumbnails

## Status
Accepted. Amends [ADR-0002](./0002-nightly-depot-sync-with-full-frames.md)
(the "no thumbnails" point).

## Context
Some work should not run inside the upload request: generating a display
thumbnail, cleaning up temp files, the daily open → fixed sweep from
ADR-0005, and the photo retention purge from ADR-0011.

ADR-0002 rejected thumbnails, but its argument is about **upload**
bandwidth from the Client, and it holds. The dashboard has the opposite
problem: a table of 50 Potholes or a timeline of Sightings rendered from
200–500 KB full frames downloads 10–25 MB. Thumbnails generated on the
Backend fix that without changing the Client contract.

A message broker (RabbitMQ, Redis) would be one more service to run on a
small VPS for a handful of jobs per night.

## Decision

### Job queue
A `jobs` table in Postgres: `id`, `type`, `sighting_id`, `status`
(`pending` | `done` | `failed`), `attempts`, `last_error`, `run_after`,
`created_at`.

- A job is inserted **in the same transaction as the Sighting**, and only
  when the Sighting row was actually inserted (not on a duplicate upload).
- A Spring `@Scheduled` poller claims due jobs with
  `SELECT ... FOR UPDATE SKIP LOCKED` and processes each in one transaction.
- Up to 3 attempts, with backoff via `run_after`; then `failed` with
  `last_error` kept for inspection.

### Job types and scheduled tasks
- **`thumbnail`** (per Sighting): long side ~400 px, JPEG quality ~80,
  path stored in `sightings.thumbnail_path`. The dashboard falls back to
  the full image while `thumbnail_path` is empty.
- **Temp-file sweep** (`@Scheduled`, hourly): deletes upload temp files
  older than ~1 h (orphans from crashes, see ADR-0008).
- **Status sweep** (`@Scheduled`, daily): applies ADR-0005's silence rule,
  moving `open` Potholes whose `last_seen_at` is 30 days old or older to
  `fixed`, and setting `fixed_at` as defined in ADR-0009.
- **Retention purge** (`@Scheduled`, daily): see ADR-0011.

### Postponed
Reverse geocoding (street names), image blurring (ADR-0011) and a
server-side detector re-check. Each would be a new job type, not a new
mechanism.

### ADR-0002 clarification
"No thumbnails" in ADR-0002 means **the Client sends no thumbnails**. The
Client still uploads only the full frame.

## Consequences

### Positive
- No extra infrastructure: jobs live and are backed up with the data.
- Job creation is atomic with the Sighting, so no Sighting is left without
  its thumbnail job and no job points to a missing Sighting.
- Dashboard pages load small images; the full frame is fetched only when
  someone opens it.

### Negative
- Polling adds a small constant load on the database.
- Thumbnails add disk usage (small next to the full frames).

### Mitigations
- Poll interval and batch size are configuration. At POC volume a
  few-second interval is plenty.
