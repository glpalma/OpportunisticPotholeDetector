# ADR-0008: Postgres, plain coordinates, images on disk

## Status
Accepted. Closes the "database still open" point of
[ADR-0004](./0004-spring-boot-and-angular.md).

## Context
ADR-0004 left the database open. The Backend has to store Sightings,
cluster them into Potholes within ~10 m, keep background jobs, dashboard
users and review marks, and hold one full-frame JPEG per Sighting.

At POC volume (one bus, a few hundred Sightings per day) none of this
needs specialized storage. The question is how little we can get away with.

## Decision

### Database
- **PostgreSQL**, schema managed with **Flyway** migrations.
- **No PostGIS.** Coordinates are plain `double precision` `lat`/`lon`
  columns with a B-tree index on `(lat, lon)`. A ~10 m radius search is a
  bounding-box prefilter on that index followed by a distance check in SQL
  (see ADR-0009).

### Images
- Image files live on disk (a Docker volume), **not in the database and not
  in object storage**. The row stores the file path.
- Upload order: write the image to a temporary file, insert the row, then
  rename the file to its final path. A crash leaves at most an orphan temp
  file, which a sweep removes (see ADR-0010).
- The idempotent insert is `INSERT ... ON CONFLICT (sighting_id) DO NOTHING`,
  not a JPA `save()`. The number of inserted rows tells the handler whether
  this is `201` (new) or `200` (duplicate).
- Uploads above the size limit are mapped to `413` (Spring's multipart
  size exception would otherwise surface as a `500`).

### Tables (minimum)

**`sightings`** — one row per Sighting, as received.
`sighting_id` (UUID, PK), `client_id`, `captured_at`, `received_at`,
`lat`, `lon`, `heading`, `speed`, `horizontal_accuracy_m`, `confidence`,
`bbox_x`, `bbox_y`, `bbox_width`, `bbox_height`, `image_path`,
`thumbnail_path`, `pothole_id` (FK), `review` (`null` | `pothole` |
`false_positive`), `reviewed_by` (FK to `users`), `reviewed_at`.
Indexes: `captured_at`, `(lat, lon)`, `pothole_id`, and a partial index on
`captured_at WHERE review IS NULL` for the review queue.

**`potholes`** — one row per derived Pothole.
`id` (UUID, PK), `lat`, `lon` (fixed at the first Sighting, see ADR-0009),
`status` (`open` | `fixed` | `resunk`), `first_seen_at`, `last_seen_at`,
`sighting_count`, `max_confidence`, `created_at`.
Indexes: `(lat, lon)`, `status`, `last_seen_at`.
The counts and dates are maintained at ingest so the dashboard never
aggregates Sightings on the fly.

**`clients`** — `client_id` (PK), `last_seen_at`, `queue_size`
(see ADR-0007).

**`jobs`** — background work queue (see ADR-0010).

**`users`** and the Spring Session tables — dashboard access
(see ADR-0013).

Column names are snake_case. `capturedAt`/`receivedAt` in the glossary
map to `captured_at`/`received_at`.

## Consequences

### Positive
- One database, one migration tool, no extensions to install.
- Images stream from disk without bloating the database or its backups.
- Duplicate uploads are cheap and race-free.

### Negative
- No real spatial index: the bounding-box prefilter is fine for thousands
  of Potholes, not for a city-wide fleet.
- Images on a local volume tie the Backend to one machine, and they have to
  be backed up separately from the database.
- Disk usage grows with full frames (ADR-0002 estimates ~250 MB per bus
  per day).

### Mitigations
- PostGIS (or object storage for images) is a migration, not a rewrite:
  coordinates and paths are already isolated in their own columns.
- Disk sizing is tracked in ADR-0012; retention in ADR-0011.
