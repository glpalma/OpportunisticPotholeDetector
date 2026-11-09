# Context

Glossary for the Opportunistic Pothole Detector. No implementation details.
Terms are the vocabulary the whole system must agree on.

## Deployment framing

Fleet-mounted client devices (rk322x TV box + camera, see ADR-0006) installed
on buses that opportunistically detect potholes as they run their routes. A
Client is not assumed to have a network while in service. Whenever it connects
to a network, it syncs immediately. It does not wait for night, and the depot
is the expected network rather than a nightly protocol (see ADR-0002). The
proof of concept starts with one bus.

The system is "opportunistic" in two senses: (1) detection piggybacks on the
bus's existing route — we don't drive to find potholes, they drive past us;
(2) upload piggybacks on whatever network the Client connects to.

## Core terms

### Sighting
A single event, produced by one Client, representing one vehicle pass by one
suspected pothole. Contiguous model detections of the same defect during the
same pass are collapsed into one Sighting on-device before syncing. A Sighting
carries at minimum: when, where, how the vehicle was moving, which Client,
how confident, and a representative image.

A Sighting is "I saw it once." It is not deduplicated across Clients or across
separate passes by the same Client.

A Sighting is only produced when a GPS fix is available at the moment of
detection. Sightings without a location do not exist (see ADR-0001).

### GPS fields on a Sighting
`lat`, `lon`, `capturedAt`, `heading`, `speed`, `horizontal_accuracy_m`.
The `capturedAt` is GPS time (when the vehicle passed the suspected pothole),
not Client wall-clock — the Client may run for hours without NTP and its
clock can drift. `capturedAt` is distinct from `receivedAt` (see Backend).

### Sighting payload (full)
A Sighting is the minimum the Client has to emit for the Backend to be able
to do anything useful later:

- `sighting_id` — UUID generated on-device at the moment of detection.
- `client_id` — which Client produced it.
- GPS fields (above), including `capturedAt`.
- `confidence` — model's confidence in `[0, 1]`.
- `bbox` — `[x, y, width, height]` in pixels of the representative frame,
  origin at the top-left. The box of the detection chosen for this Sighting.
- `image` — required. The full-resolution representative frame
  (highest-confidence frame of the collapsed burst). A Sighting without
  this frame is rejected. See ADR-0002.

The Backend persists everything in the payload plus one Backend-side field:

- `receivedAt` — when the Backend accepted the upload. Distinct from
  `capturedAt`: a Sighting captured at 14:00 may not be received until the
  Client next connects. Clustering, aging, and sync-lag observability must
  be explicit about which time they mean.

No class or severity is emitted by the Client. The frame preserves all
information needed to derive those server-side with better/newer models
without re-collecting data.

### Pothole
A real-world road defect. Not reported directly by any Client; derived on the
Backend by clustering Sightings across Clients and time. A Pothole's confidence
is a function of how many Sightings support it, from how many distinct Clients,
over what time window.

A Pothole's position is the position of its first Sighting and does not
move as more Sightings attach (see ADR-0009).

The dashboard shows Potholes, not raw Sightings. A Sighting is shown so a
reviewer can confirm it or mark it a false positive (see ADR-0014).

### Pothole status
`open`, `fixed`, or `resunk`. Stored on the Pothole. Judged from Sighting
`capturedAt`, never from `receivedAt`.

- **open** — the latest Sighting `capturedAt` is less than 30 days ago.
- **fixed** — the latest Sighting `capturedAt` is 30 days ago or older.
  A presumption, not proof of repair: a bus may simply have stopped
  driving that street.
- **resunk** — the Pothole was fixed, and a later Sighting attached to it.
  A late upload is still a comeback (see ADR-0005).

**Neglected** is not a status. It describes an open Pothole whose first
Sighting `capturedAt` is 30 days ago or older: still being seen, and has
been for too long.

A Pothole is not deleted because it went quiet. See
[ADR-0005](docs/adr/0005-pothole-status.md).

A **false-positive Pothole** is one whose Sightings have all been reviewed as
false positives. It keeps its status; the dashboard hides it by default.

### Review
A team member's verdict on one Sighting: **pothole** (the detector was right)
or **false positive**. A Sighting is unreviewed until someone marks it. Reviews
do not change clustering or Pothole status. Detector precision is the share of
reviewed Sightings marked pothole.

### Client
A bus-mounted rk322x TV box + camera (ADR-0006) running the on-device
detector and local buffer. Produces Sightings. Owns the dedup of contiguous
frames. Identified by `client_id`.

### Depot
The physical location where buses return at end-of-service, and the usual
place a Client gets a network (the Depot's Wi-Fi). It is not a nightly
sync window. Any network connection starts a sync (see ADR-0002).

Depot power and Wi-Fi are a given (stipulated by the fleet operator). A
stay there may last hours. The sync protocol still has to make progress
on a shorter connection, because the Client syncs whenever a network
appears.

### Backend
Receives one Sighting per request (ADR-0003). A request never carries a
batch. Persists each Sighting and derives Potholes from them. Also serves
the dashboard.

### Dashboard user
A member of the project team with an account on the dashboard. Views
Potholes and records Reviews on Sightings, including false positives.
There is no public access (ADR-0011).

## Retired terms

- **Occurrence** — ambiguous between Detection, Sighting, and Pothole.
  Replaced by Sighting (client-side event) and Pothole (backend-side cluster).
