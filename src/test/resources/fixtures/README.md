# Test fixtures

A tiny hand-written GTFS feed for unit tests. Target stop: `S1` (Plaza Eliptica).
Valid from 2026-11-01 to 2026-12-31.

## Services

- `WEEKDAY`: Monday to Friday. Removed on 2026-12-08 (holiday).
- `WEEKEND`: Saturday and Sunday. Added on 2026-12-08 (holiday).

## Departures at S1

| trip | route | service | time     | note                              |
|------|-------|---------|----------|-----------------------------------|
| T1   | L1    | WEEKDAY | 08:00:00 |                                   |
| T2   | L1    | WEEKDAY | 08:15:00 |                                   |
| T3   | L2    | WEEKDAY | 08:30:00 | different route                   |
| T4   | L1    | WEEKDAY | 09:00:00 |                                   |
| T5   | L1    | WEEKDAY | 09:30:00 | 4th departure: must be cut by "next 3" |
| T6   | L1    | WEEKEND | 10:00:00 | must not appear on weekdays       |
| T7   | L2    | WEEKDAY | 25:10:00 | after midnight (01:10 next day)   |

`T8` only serves `S2` (08:20) and must never appear for `S1`.

## Dates useful in tests

- 2026-11-12 Thursday: WEEKDAY runs.
- 2026-11-14 Saturday: WEEKEND runs.
- 2026-12-08 Tuesday: holiday, WEEKEND runs instead of WEEKDAY.
- 2026-12-31 is the last valid day; 2027-01-01 has no service.

## Scenarios

- At 07:00 on a weekday: T1, T2, T3.
- At 08:20 on a weekday: T3, T4, T5.
- At 09:10 on a weekday: T5, T7 (only 2 left).
- At 00:30 on a weekday: the previous day's T7 (25:10) is still ahead, so a
  correct implementation also looks at the previous day's service.
- On the holiday 2026-12-08: only T6.
