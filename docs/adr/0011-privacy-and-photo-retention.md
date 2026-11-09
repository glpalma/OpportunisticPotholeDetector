# ADR-0011: Privacy (LGPD) and photo retention

## Status
Accepted. Complements [ADR-0002](./0002-sync-on-connect-with-full-frames.md)
and [ADR-0005](./0005-pothole-status.md).

## Context
Every Sighting carries a full camera frame of a public street. Frames can
contain people, faces and licence plates, which is personal data under the
LGPD. The POC is a university research project.

ADR-0005 says Pothole and Sighting rows are not deleted by age. That is
about the records. The images are a separate question: they are the part
that carries personal data, and they are also the part that takes the most
disk.

## Decision

### Legal basis and responsibility
- The expected legal basis is studies by a research body (LGPD art. 7,
  IV). **To be confirmed with the university's DPO / project advisor**
  before collecting data on real routes.
- Mounting a camera on public buses involves the fleet operator. Who is
  responsible for the data (controller) is part of the same confirmation.

### Capture
- The camera is angled at the road surface, not forward at the street,
  to minimize people and plates in frame.

### Access
- Images and thumbnails are served only through authenticated Backend
  endpoints (ADR-0013). The reverse proxy never serves the image folder
  directly.
- Nothing is public. Any future public view must use blurred images.

### Blurring
- No automatic blurring for the POC.
- When needed, it becomes a `blur` job that runs before the thumbnail job
  (ADR-0010).

### Retention
- Images are kept for a fixed period: **default 6 months, configurable**.
- A daily purge deletes the image and thumbnail files of Sightings older
  than that (by `captured_at`) and clears `image_path`/`thumbnail_path`.
  **The Sighting row is kept**, so ADR-0005 still holds: Potholes and their
  history are never deleted by age.
- The dashboard shows a placeholder for purged images.

## Consequences

### Positive
- Personal data has a bounded lifetime and a documented basis.
- Disk usage stops growing after the retention period.
- Clustering, status and statistics are unaffected by the purge.

### Negative
- Purged frames are gone for false-positive auditing and **for model
  retraining**, which ADR-0002 lists as a reason to keep full frames.
- The road-angled camera may see less of the pothole's surroundings than a
  forward one, which may affect detection.

### Mitigations
- If retraining needs older frames, add a way to copy selected,
  blurred frames into a training set before the purge. Not designed yet.
- The retention period is configuration; it can change after the DPO
  conversation without code changes.
