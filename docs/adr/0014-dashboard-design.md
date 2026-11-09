# ADR-0014: Dashboard design

## Status
Accepted. Closes the "map library still open" point of
[ADR-0004](./0004-spring-boot-and-angular.md) and the "dashboard scope"
branch in `grill-progress.md`.

## Context
The dashboard shows Potholes, not a feed of raw Sightings (CONTEXT.md).
Sightings are opened where a person has to judge one frame: on the Pothole,
and in the review queue. A review marks that Sighting as a pothole or a
false positive. With a minimum support of one Sighting, a detector false
positive is also a Pothole; hiding Potholes whose Sightings are all false
positives keeps those off the map by default.

Data arrives when a Client connects (ADR-0002), so the dashboard does not
need to be live.

## Decision

### Stack
- **Angular** (ADR-0004) with **Angular Material** for tables, filters,
  dialogs and the login form.
- **Leaflet** (through an Angular wrapper such as ngx-leaflet) with
  **OpenStreetMap** tiles and attribution, and `leaflet.markercluster`
  for clustering markers. OSM's tile usage policy is fine for a small team.
- UI language: **Portuguese (pt-BR)**. Code, API and docs stay in English.
- Desktop first. Map and detail pages are usable on a phone; the review
  queue is meant for a keyboard.

### Potholes on the map, Sightings only for review
Map and table show **Potholes**. They do not list raw Sightings. The
Pothole detail page lists that Pothole's Sightings so a reviewer can
confirm one or mark it a false positive. The review queue is the same
job, one unreviewed Sighting at a time.

### Status labels
Stored status (ADR-0005) and derived neglect are shown as:

| State | Label |
| --- | --- |
| `open`, not neglected | Aberto |
| `open`, neglected | Negligenciado |
| `fixed` | Sem registro há 30+ dias |
| `resunk` | Reaberto |

`fixed` is deliberately not labelled "Consertado": it is a presumption
(ADR-0005). **Neglected is computed by the Backend** and returned as a
flag, so the 30-day windows live in one place.

### Review
- A Sighting can be marked **"É buraco"** (`pothole`) or **"Falso
  positivo"** (`false_positive`), or the mark can be cleared. Author and
  time are stored (ADR-0008).
- Concurrent marks: last write wins.
- Marks do not change clustering or status (ADR-0009). A Pothole whose
  Sightings are all `false_positive` is hidden by default.
- **Detector precision** = Sightings marked `pothole` ÷ all reviewed
  Sightings.

### Screens
1. **Login.**
2. **Map (home)** — Pothole markers coloured by status, clustered. Filter
   panel: status, date range of last Sighting, minimum confidence, only
   Potholes with unreviewed Sightings, show false-positive Potholes.
   Clicking a marker opens a summary with a link to the detail page.
   A **stats card** above the map: Potholes by status, Sightings reviewed
   vs pending, detector precision.
3. **Table** — same filters; columns: thumbnail, first and last Sighting,
   number of Sightings, max confidence, status, review progress (e.g.
   "2/5 revisados"). Sortable and paginated.
4. **Pothole detail** — small map, status with a one-line explanation,
   timeline of Sightings (thumbnail, `capturedAt`, `receivedAt`,
   confidence, speed, GPS accuracy). Clicking a Sighting opens the full
   frame with the bbox drawn on it and the review buttons.
5. **Review queue** — unreviewed Sightings, oldest `capturedAt` first, one
   at a time: full frame with bbox, plus Pothole context (small map,
   number of other Sightings). Shortcuts: **B** = É buraco, **F** = Falso
   positivo, **P** = Pular, **Z** = Desfazer. The next image is preloaded.
6. **Bus status card** in the header — last sync (latest `receivedAt`),
   queue size from the last health check (ADR-0007), number of Sightings
   in the last sync.

Purged images (ADR-0011) show a placeholder.

### Refresh and time
- Data loads on navigation, plus a refresh button. No polling, no
  server-sent events.
- Dates are shown as `dd/MM/yyyy HH:mm` in `America/Sao_Paulo`.
  The API always uses ISO 8601 UTC.

### Read API (`/app/api`, session-authenticated, ADR-0013)
Dashboard reads are not part of the versioned Client contract (ADR-0003)
and can change with the dashboard.

| Endpoint | Purpose |
| --- | --- |
| `POST /login`, `POST /logout`, `GET /me` | Session (ADR-0013) |
| `GET /potholes/map` | Compact points (`id`, `lat`, `lon`, `status`, `neglected`) for the visible map area (`bounds=minLon,minLat,maxLon,maxLat`) and filters; capped at a few thousand |
| `GET /potholes` | Paginated, sortable table with the same filters |
| `GET /potholes/{id}` | Pothole detail with its Sightings |
| `GET /sightings/{id}/image` | Full frame, streamed from disk |
| `GET /sightings/{id}/thumbnail` | Thumbnail (falls back to the full frame) |
| `GET /review-queue/next` | Next unreviewed Sighting(s) for the queue |
| `PUT /sightings/{id}/review` | `{"verdict": "pothole" \| "false_positive" \| null}` |
| `GET /stats` | Numbers for the stats card |
| `GET /clients` | Bus sync status (ADR-0007) |
| `GET /export/sightings.csv` | CSV export (below) |

Filters shared by the map, table and export: `status`, `neglected`,
`from`/`to` (last Sighting `capturedAt`), `minConfidence`, `unreviewed`,
`includeFalsePositives`.

Image responses use `Cache-Control: private, max-age=..., immutable`:
a Sighting's image never changes.

Pothole fields returned: `id`, `lat`, `lon`, `status`, `neglected`,
`firstSeenAt`, `lastSeenAt`, `sightingCount`, `maxConfidence`,
`reviewedCount`, `falsePositive` (all Sightings marked false positive).

### Export
One CSV of Sightings, filtered like the table: every Sighting field
(except file paths) plus `pothole_id`, `review`, `reviewed_by`,
`reviewed_at`. Enough to analyse precision in a spreadsheet or notebook.

## Consequences

### Positive
- The map stays readable while every frame remains auditable.
- Review data produces a precision number and, later, a labelled set for
  retraining.
- No real-time infrastructure.
- The read API is small and shaped by the screens.

### Negative
- One more screen (review queue) than a minimal POC.
- OSM's public tiles are not for heavy use.
- pt-BR only; an English UI would need Angular i18n later.

### Mitigations
- If tile usage grows, switch to a hosted tile provider; Leaflet only needs
  a new tile URL.
