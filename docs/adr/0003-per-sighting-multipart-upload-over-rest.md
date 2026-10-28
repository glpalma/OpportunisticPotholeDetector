# ADR-0003: Per-Sighting multipart upload over REST

## Status
Accepted

## Context
We have to pick the wire shape for Clients syncing Sightings to the Backend
during the nightly depot window. Two decisions were tangled together:

1. **Transport**: REST/HTTP vs gRPC.
2. **Granularity**: batched (N Sightings per request) vs per-Sighting.

### Transport

gRPC's selling points — bidirectional streaming, strongly typed stubs,
multiplexing — buy us little here. We are not doing a continuous feed; we
are doing one burst per Client per night. The upload path is one-way
Client → Backend. Any Pi has `curl` and HTTP client libraries out of the
box; adding gRPC pulls in protoc, generated stubs, and HTTP/2 tuning for
no concrete win. Firewall and depot-network stories are also simpler over
plain HTTPS.

### Granularity

An earlier design iteration assumed we'd batch "hundreds of Sightings per
request" to amortize HTTP overhead. That assumption was imported from
"spotty mobile" thinking and does not survive the setting established by
[ADR-0002](./0002-nightly-depot-sync-with-full-frames.md):

- Images are 200–500 KB each. A batch of 500 Sightings is 100–250 MB per
  request — above default server body-size caps, hostile to resumability,
  and ugly for partial-failure reporting.
- A dropped Wi-Fi connection mid-batch forces re-sending the whole batch.
- One corrupt image fails the whole batch.
- Both sides have to hold the whole batch in memory to serialize.

The alternative — splitting metadata (batch POST) from images (per-Sighting
PUT) — avoids the body-size and resumability issues, but introduces an
"orphan metadata" state the Backend must tolerate forever: rows whose
image never arrived because the Client died between the two calls.

Per-Sighting upload sidesteps both. The only cost is HTTP overhead per
request: ~1 KB of headers × ~500 Sightings/day ≈ 500 KB of waste over a
link where we have hours. Immaterial.

The Client has limited memory and will delete each Sighting after upload.
Idempotency therefore matters — if the Backend saves a Sighting but the
ACK is lost before the Client receives it, the Client will retry on
reconnect. We get this for free by making `sighting_id` a Client-generated
UUID that the Backend uses as a natural key.

## Decision

### Transport
Plain REST over HTTPS.

### Shape
**One Sighting = one `multipart/form-data` POST to `/v1/sightings`.**

The Client contract is versioned from the start. A later incompatible
upload shape ships at `/v2/...`. Clients already in the field keep
posting to `/v1/sightings`. Dashboard reads are not part of this contract.

- The multipart body contains two parts:
  - `metadata` — JSON with `sighting_id`, `client_id`, GPS fields, `confidence`, `bbox`.
  - `image` — the full-resolution representative JPEG.
- The Backend treats `sighting_id` as a natural key. Re-POSTing the same
  `sighting_id` changes nothing.
- The Client deletes its local copy of a Sighting **only** after `200` or
  `201`. No local upload-state tracking is required.

### Status codes

| Code | Meaning | Client does |
| --- | --- | --- |
| `201` | Sighting stored | Delete the local copy |
| `200` | Same `sighting_id` already stored | Delete the local copy |
| `400` | Metadata does not match the contract (missing field, bad JSON, bad `bbox`, value out of range, `sighting_id` not a UUID) | Keep the local copy and do not retry that payload |
| `415` | `image` is missing or its bytes are not a JPEG | Keep the local copy and do not retry that payload |
| `5xx` | The row or the image could not be stored | Keep the local copy and retry later |
- There is no batching layer. The Client sends Sightings one at a time
  during the sync window, in whatever order it likes, with whatever
  concurrency the depot link tolerates.

## Consequences

### Positive
- Simplest possible mental model: one Sighting = one request = one row + one blob.
- No orphan-metadata state. No two-phase commit on the Client.
- Resumable at Sighting granularity for free. A dropped connection costs
  at most one in-flight Sighting, not a batch.
- Partial failure is trivial: failed Sightings stay on the Client and
  retry next pass.
- Any HTTP stack on either side works. No special infra.

### Negative
- ~500 HTTP requests per Client per night. Over depot Wi-Fi with a
  multi-hour window, this is minutes of sync, not hours — but it does
  mean the Backend has to handle burst concurrency when many buses hit
  the depot simultaneously.
- No single "I'm done syncing" transaction. The Backend infers sync
  completion from "Client stopped POSTing," which is fine for Pothole
  derivation (clustering tolerates late arrivals) but means there is no
  natural moment to compute per-Client sync metrics unless the Client
  emits a final `/v1/sync-complete` ping (optional, out of scope for this ADR).

### Mitigations
- The Client may upload with small concurrency (e.g. 2–4 in flight) to
  mask per-request latency without stressing the Backend. Exact number
  is a tuning knob, not an architectural decision.
- If depot-wide burst load turns out to be a real problem, the Backend
  can rate-limit per `client_id` without any Client-side change.
- A future ADR can introduce an optional end-of-sync marker if sync
  observability turns out to need it.
