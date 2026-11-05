# ADR-0006: Silence and neglect are separate configured durations

## Status
Accepted

## Context
[ADR-0005](0005-pothole-status.md) uses two durations. The silence window
decides when an open Pothole becomes fixed. The neglect window decides
when an open Pothole has been seen for too long. They were both written
as 30 days.

Those are different facts. A repaired hole stops being seen after about
a week in the Brazilian cities that publish a number (São Paulo's mean
is about 6–7 days, against a target under 10; Belo Horizonte's deadline
is 5 business days; slower published city figures run to about 12 days).
Keeping it open for another month labels it neglected while the silence
clock is still running. A hole that is still being seen a month later
has missed that repair cycle, which is the neglect fact.

The lengths will move as routes, detector recall, and city practice
become known. A literal in code makes that a code change.

## Decision
The Backend reads two durations from configuration. They are not columns,
and they are not literals in the status rules.

| Duration | Property | Environment variable | Default |
|---|---|---|---|
| Silence window | `pothole.silence-window-days` | `POTHOLE_SILENCE_WINDOW_DAYS` | 14 |
| Neglect window | `pothole.neglect-window-days` | `POTHOLE_NEGLECT_WINDOW_DAYS` | 30 |

Spring Boot relaxed binding is enough. An environment variable overrides
the property. No second config mechanism.

Fourteen days of silence is two weeks of bus service. A rainy stretch or
a holiday week can suppress detections without the hole being gone.
Seven days treats that gap as a repair and then as a comeback.

Thirty days of neglect stays past the silence window plus a slow normal
repair (14 + 12). An on-time repair goes fixed before it can be called
neglected. A hole still being seen at 30 days has outlived the published
municipal cycle.

The silence window must be shorter than the neglect window. The Backend
refuses to start otherwise. Equal windows recreate the false-neglect
tail this decision removes.

## Consequences

### Positive
- Changing either duration is a config edit and a restart.
- The default pair matches a week-scale city repair without calling that
  repair neglect.

### Negative
- A deploy can change the labels of existing Potholes without a migration.
  The daily sweep applies whatever the config says that day.
- The defaults are national urban figures. A corridor with a 48-hour
  repair rule needs its own value. Road class is not modeled yet.
