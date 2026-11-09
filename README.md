# OpportunisticPotholeDetector

Bus-mounted Clients (rk322x TV box + camera) detect potholes and upload
Sightings to a Spring Boot backend. Each request is one Sighting, and each
Sighting includes a required full frame. A Client syncs whenever it
connects to a network, not only overnight at the depot. The dashboard is
Angular and shows Potholes; Sightings are opened to confirm a detection or
mark a false positive. The three programs live in `backend/`, `frontend/`,
and `client/`.

A Pothole is presumed fixed after the silence window and neglected after
the neglect window. The defaults are 14 days and 30 days, and both are
configuration
([ADR-0006](docs/adr/0006-silence-and-neglect-windows.md)).

Glossary: [CONTEXT.md](CONTEXT.md). Decisions: [docs/adr/](docs/adr/).
Design status and open questions: [docs/grill-progress.md](docs/grill-progress.md).
