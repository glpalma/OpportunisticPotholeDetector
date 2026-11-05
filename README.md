# OpportunisticPotholeDetector

Bus-mounted Clients detect potholes and upload Sightings to a Spring Boot
backend. The dashboard is Angular. The on-device Client is a separate
program on the Pi. The three live in `backend/`, `frontend/`, and `client/`.

`GET /health` returns `200` and `{"status":"up"}` when the backend process
is serving HTTP. Clients use it to tell a dead depot link from a backend
that will accept Sightings
([ADR-0007](docs/adr/0007-health-probe.md)).

Glossary: [CONTEXT.md](CONTEXT.md). Decisions: [docs/adr/](docs/adr/).

A Pothole is presumed fixed after the silence window and neglected after
the neglect window. The two durations are configuration, not code
([ADR-0006](docs/adr/0006-silence-and-neglect-windows.md)).