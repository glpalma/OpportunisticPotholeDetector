# ADR-0006: The Client runs on an rk322x TV box

## Status
Accepted

## Context
The earlier docs assumed a Raspberry Pi + camera per bus. The hardware
actually available for the POC is an rk322x TV box, provided by the
university for this project, running Armbian.

The rk322x is a quad-core Cortex-A7 with no neural accelerator. That is
noticeably weaker than a recent Pi, and the whole Client design assumes
the detector runs on the device: it produces `confidence` and `bbox`, and
it collapses contiguous frames into one Sighting.

The POC starts with **one bus and one Client**.

## Decision
The Client is a bus-mounted rk322x TV box running Armbian, with a camera
and an external GPS module.

- The Client's system clock is set from GPS (`gpsd` + `chrony`), so it is
  correct even without NTP. `capturedAt` is still taken from GPS time
  (see CONTEXT.md).
- The depot Wi-Fi network is pre-configured on the Client. TLS certificate
  verification stays on.
- On-device detection is **assumed to be feasible** on this hardware. It has
  not been tested yet.

## Consequences

### Positive
- Hardware is already available at no cost to the project.
- Armbian is a regular Debian-based Linux, so the Client program, `gpsd`,
  `chrony` and an HTTP client work as they would on a Pi.

### Negative
- Detector throughput is the main project risk. If the box only processes
  about one frame per second, a bus at 40 km/h covers ~11 m between frames
  and will miss potholes.
- On-device storage is the box's eMMC (often 8–16 GB), which bounds how many
  unsynced nights the Client can absorb (see ADR-0002).

### Mitigations
- Benchmark a small quantized detector on the box (frames per second) before
  building the rest of the Client. If it is too slow, revisit this ADR
  before revisiting anything else.
- The open branches "detector model", "on-device dedup specifics" and
  "on-device retention policy" in `docs/grill-progress.md` are now sized
  for this hardware, not for a Pi.
