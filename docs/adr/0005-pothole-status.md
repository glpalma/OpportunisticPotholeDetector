# ADR-0005: Pothole status is open, fixed, or resunk

## Status
Accepted. Window lengths are configuration in [ADR-0006](./0006-silence-and-neglect-windows.md). [ADR-0011](./0011-pothole-clustering-position-and-locking.md) records where a Pothole sits and how clustering is locked. It does not change the resunk rule below: any Sighting that attaches to a `fixed` Pothole marks it `resunk`.

## Context
A Pothole that stops receiving Sightings might have been repaired, or the
buses might have stopped driving that street. We still want the dashboard
to separate three situations: still being seen, gone quiet, and quiet and
then seen again. A fourth concern, "still being seen for too long," is
neglect, which is not the same fact as silence.

Deleting the row after a TTL throws away the repair and the comeback.
Computing the label only at read time can express open versus quiet, and
it can express neglect from the first and latest `capturedAt`. It cannot
express resunk, because that depends on remembering that the Pothole had
already been fixed.

`receivedAt` is the wrong clock. A Sighting captured in the afternoon can
arrive the next time the Client connects. An upload clock would mark a hole
fixed while a bus had already seen it.

## Decision
Each Pothole has a stored status: `open`, `fixed`, or `resunk`.

- A new Pothole starts `open`.
- It becomes `fixed` when its latest Sighting `capturedAt` is at or past
  the silence window. Nothing else about the row is removed.
- It becomes `resunk` when a new Sighting attaches to a `fixed` Pothole.
  That happens in the upload request, not later. A later sweep would turn
  the same row back into an ordinary open Pothole and lose the comeback.
- Further Sightings on a `resunk` Pothole leave the status `resunk`.
- **Neglected** is not stored. A Pothole is neglected when its status is
  `open` and its first Sighting `capturedAt` is at or past the neglect
  window.

The silence window and the neglect window are durations, not columns.
They answer different questions: silence asks when the Pothole was last
seen, neglect asks when it was first seen while it is still `open`.
They are not the same length, and neither length is a literal in code.
See [ADR-0006](./0006-silence-and-neglect-windows.md).

Silence means "we have not seen it," not "a bus passed and the detector
did not fire." The system has no record of passes without a detection.

## Consequences

### Positive
- Fixed, neglected, and resunk stay distinct.
- Resunk survives because it is written at the moment the new Sighting arrives.
- Quiet Potholes remain queryable. Nothing is deleted by age.

### Negative
- A route change, a parked fleet, or a missed detection looks like a repair.
- A daily sweep is required for the open → fixed transition. Ingest never
  observes time passing with no request.
- One status cannot record a second fix/resink cycle. A resunk Pothole
  stays resunk while Sightings continue. A history of transitions is a
  later addition.
