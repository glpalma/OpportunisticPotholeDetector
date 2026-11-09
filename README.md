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

## Backend

The Backend is a Spring Boot app in `backend/`. Each feature
(`client`, `health`, `sighting`) is split into `domain`, `data`, and
`api`. Shared security and rate limiting stay in `config`. Compose runs
the app with Postgres and a volume for Sighting images. The API key is
`POTHOLE_API_KEY` in [backend/.env](backend/.env). Compose reads that file.
The checked-in value is `local-dev-key`. Plain HTTP is only for this local
stack; the deployed Backend is HTTPS.

From `backend/`:

```powershell
docker compose up --build
```

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
