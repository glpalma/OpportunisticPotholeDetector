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

The Backend is a Spring Boot app in `backend/`. `GET /v1/health` returns
`200` and `{"status":"up"}` when the process is serving HTTP.

From `backend/`:

```powershell
.\gradlew.bat bootRun
```

Or with Docker:

```powershell
docker compose up --build
```

Then:

```powershell
curl.exe -i http://localhost:8080/v1/health
```

Expect `200` and `{"status":"up"}`.
