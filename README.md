# OpportunisticPotholeDetector

Bus-mounted Clients (rk322x TV box + camera) detect potholes and upload
Sightings to a Spring Boot backend. Each request is one Sighting, and each
Sighting includes a required full frame. A Client syncs whenever it
connects to a network, not only overnight at the depot. The dashboard is
Angular and shows Potholes; Sightings are opened to confirm a detection or
mark a false positive. The three programs live in `backend/`, `frontend/`,
and `client/`.

Glossary: [CONTEXT.md](CONTEXT.md). Decisions: [docs/adr/](docs/adr/).

A Pothole is presumed fixed after the silence window and neglected after
the neglect window. The two durations are configuration, not code
([ADR-0006](docs/adr/0006-silence-and-neglect-windows.md)).

## Run the stack

From the repository root, Compose starts Postgres, the Spring Boot app, and
Caddy. Caddy serves the Angular dashboard and proxies `/v1` and `/app/api`
to the app. It does not serve the image volume.

```powershell
docker compose up --build
```

Open the dashboard at [http://localhost/](http://localhost/). The map opens
on Campinas. If that view has no Potholes and some exist elsewhere, it
moves to them. The Client API stays on [http://localhost:8080](http://localhost:8080/)
as well as through Caddy. The API key defaults to `local-dev-key`. Set
`POTHOLE_API_KEY` to override it. Plain HTTP is only for this local stack.

`backend/compose.yaml` still starts only the API and Postgres, without the
dashboard.

## Dashboard

The dashboard is an Angular app in `frontend/`. UI copy is Portuguese
(pt-BR). It shows Potholes on a map and in a table, a Pothole detail page,
and a review queue. There is no login in this POC: `/app/api` is open on
the local stack. Reviews store the verdict and time and leave
`reviewed_by` empty.

Dates are shown as `dd/MM/yyyy HH:mm` in `America/Sao_Paulo`. The API uses
ISO 8601 UTC.

For UI work without rebuilding the image, run the API (root Compose, or
`backend/compose.yaml`) and from `frontend/`:

```powershell
npm install
npm start
```

`ng serve` proxies `/app/api` to `http://localhost:8080`.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/app/api/potholes/map?bounds=minLon,minLat,maxLon,maxLat` | Points for the visible map |
| `GET` | `/app/api/potholes` | Paginated table |
| `GET` | `/app/api/potholes/{id}` | Detail and its Sightings |
| `GET` | `/app/api/sightings/{id}/image` | Full frame |
| `GET` | `/app/api/sightings/{id}/thumbnail` | Thumbnail, or the full frame if none exists |
| `GET` | `/app/api/review-queue/next` | Oldest unreviewed Sightings |
| `PUT` | `/app/api/sightings/{id}/review` | `{"verdict":"pothole"\|"false_positive"\|null}` |
| `GET` | `/app/api/stats` | Counts and detector precision |
| `GET` | `/app/api/clients` | Bus sync status |
| `GET` | `/app/api/export/sightings.csv` | Filtered Sighting CSV |

Shared filters: `status`, `neglected`, `from`, `to`, `minConfidence`,
`unreviewed`, `includeFalsePositives`. False-positive Potholes are hidden
unless `includeFalsePositives=true`. The review queue accepts an optional
cursor, `afterCapturedAt` and `afterId`, so Pular can move past a Sighting
without marking it.

Silence and neglect windows default to 14 and 30 days
(`POTHOLE_SILENCE_WINDOW_DAYS`, `POTHOLE_NEGLECT_WINDOW_DAYS`). The app
refuses to start if silence is not shorter than neglect. A daily job marks
quiet open Potholes `fixed`.

## Backend

The Backend is a Spring Boot app in `backend/`. Each feature
(`client`, `health`, `sighting`, `dashboard`) is split into `domain`,
`data`, and `api`. Shared security and rate limiting stay in `config`.
The API key is `POTHOLE_API_KEY`. When the app runs from `backend/` with
`docker compose`, Compose reads [backend/.env](backend/.env). The local
default is `local-dev-key`. Plain HTTP is only for this local stack; the
deployed Backend is HTTPS.

`GET /health` is an unauthenticated liveness check:

```powershell
curl.exe -i http://localhost:8080/health
```

Expect `200` and `{"status":"up"}`.

`GET /v1/health` is the Client probe. It requires `X-API-Key` and checks
the database. `X-Client-Id` and `X-Queue-Size` update that bus's sync
status.

```powershell
curl.exe -i http://localhost:8080/v1/health -H "X-API-Key: local-dev-key" -H "X-Client-Id: bus-1" -H "X-Queue-Size: 3"
```

Expect `200` and `{"service":"pothole-api","status":"ok","time":"..."}`.
A wrong key is `401`. If Postgres is down the body is
`{"service":"pothole-api","status":"db_unavailable"}` with `503`.

`POST /v1/sightings` accepts one Sighting: a `metadata` JSON part and an
`image` JPEG part. The same `sighting_id` again is `200` and changes
nothing. A new Sighting is `201`.

```powershell
curl.exe -i -X POST http://localhost:8080/v1/sightings -H "X-API-Key: local-dev-key" -F 'metadata={"sighting_id":"3f1c2c2e-0b1a-4d5e-9f70-1a2b3c4d5e6f","client_id":"bus-1","lat":-23.5505,"lon":-46.6333,"capturedAt":"2026-11-09T14:00:00Z","heading":90,"speed":8.5,"horizontal_accuracy_m":4.2,"confidence":0.91,"bbox":[10,20,30,40]};type=application/json' -F "image=@frame.jpg;type=image/jpeg"
```

`frame.jpg` is a real JPEG from the Client. Metadata that fails
validation is `400`. A missing or non-JPEG image is `415`. An image over
the configured maximum (default 3 MB) is `413`.

`GET /v1/sightings` lists stored Sightings, newest `capturedAt` first.
`page` starts at `1` and defaults to `1`. `pageSize` defaults to `50`
and must be from `1` to `100`. A `page` or `pageSize` outside that range
is `400`. The body is a JSON array of that page. Each object has the
stored fields (`sightingId`, `clientId`, `capturedAt`, `receivedAt`,
`lat`, `lon`, `heading`, `speed`, `horizontalAccuracyM`, `confidence`,
`bbox`, `potholeId`, `review`, `reviewedBy`, `reviewedAt`) and omits
image file paths. Times are ISO 8601 UTC. A missing or wrong key is
`401`.

```powershell
curl.exe -i "http://localhost:8080/v1/sightings?page=1&pageSize=50" -H "X-API-Key: local-dev-key"
```

`.\gradlew.bat bootRun` still runs the app directly. It needs
`POTHOLE_API_KEY` and a Postgres database at
`jdbc:postgresql://localhost:5432/pothole` (`pothole` / `pothole`), unless
those are overridden.
