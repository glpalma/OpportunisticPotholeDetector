# ADR-0001: Drop Sightings when there is no GPS fix

## Status
Accepted

## Context
Clients are vehicle-mounted and rely on an external USB GPS module. A fix is
not always available: cold-start takes 30s–several minutes, urban canyons
degrade accuracy, tunnels and underground garages lose signal entirely, and
cheap modules occasionally drop a fix mid-trip.

The detector can still fire during no-fix windows. We have to decide what to
do with those detections.

Three options were considered:

1. **Drop the Sighting entirely.** No location, no record.
2. **Buffer the Sighting and backfill** the location from the next good fix
   (or interpolate between the last known fix and the next).
3. **Store the Sighting with a null location** and let the Backend decide.

## Decision
Option 1. If there is no GPS fix at the moment of detection, no Sighting is
produced.

## Consequences

### Positive
- Every Sighting in the system has a trustworthy location. The Backend never
  has to reason about "maybe-located" rows.
- Schema stays simple: GPS fields are non-nullable.
- Clustering into Potholes stays clean; no "unknown-location" bucket to
  handle in the dashboard.
- Opportunistic sensing: the same stretch of road gets driven again. One
  missed Sighting is cheap; a poisoned dataset is not.

### Negative
- We lose Sightings in tunnels, garages, and cold-start windows. For some
  deployments (urban-canyon-heavy, short trips) this could be a significant
  fraction.
- No way to retroactively change our minds — we have no record the detection
  ever happened.

### Mitigations
- Log no-fix drop counts locally so we can measure the loss and revisit
  this decision if it turns out to be material.
- Nothing stops a future ADR from introducing an opt-in "unlocated detection"
  stream for research, kept strictly separate from Sightings.
