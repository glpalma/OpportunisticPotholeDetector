# ADR-0007: Clients probe GET /health

## Status
Accepted

## Context
A Client at the depot can fail to upload because the Backend is down, or
because the Pi has no route to it: Wi-Fi associated but no path, a captive
portal, or the wrong host. `POST /v1/sightings` is a poor probe. A failed
POST looks like a bad payload or a `5xx`, and a captive portal can answer
`200` with an HTML page.

The upload contract in
[ADR-0003](0003-per-sighting-multipart-upload-over-rest.md) is versioned.
A connection check is not that contract. Clients already in the field and
clients on a future `/v2` upload need the same URL.

## Decision
`GET /health` returns `200` and `{"status":"up"}` when this process is
serving HTTP.

- No request body. No version prefix.
- Any other status, or a body that is not that JSON, means the Client is
  not talking to the Backend. Keep the local Sightings and retry later.
- The probe does not check the database or clustering. It answers "is the
  HTTP server up."

## Consequences

### Positive
- A Client can tell a dead link or a captive portal from a Backend that
  will accept Sightings.
- The check is one request and stays put when the upload shape moves to
  `/v2`.

### Negative
- A `200` does not mean a Sighting will be stored. A full disk, a down
  database, or a later `5xx` can still reject the upload.
