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

`T8` only serves `S2` (08:20) and `S3` (08:30); it must never appear for `S1`.

Every trip continues past `S1` to `S3` (or starts before `S1` at `S2`), so `S1` is
never the last stop of any trip. This keeps all departures boardable under the
pickup-rules filter added in task 2.

## Dates useful in tests

- 2026-11-12 Thursday: WEEKDAY runs.
- 2026-11-14 Saturday: WEEKEND runs.
- 2026-12-08 Tuesday: holiday, WEEKEND runs instead of WEEKDAY.
- 2026-12-31 is the last valid day; 2027-01-01 has no service.

## Scenarios

"Next departures" rolls over into the following days until enough are found.

- Thu 2026-11-12 07:00: T1, T2, T3.
- Thu 2026-11-12 08:20: T3, T4, T5.
- Thu 2026-11-12 09:10: T5, then T7 (01:10 Fri), then Friday's T1 (08:00).
- Fri 2026-11-13 00:30: Thursday's T7 (25:10 = 01:10 Fri) comes first, so an
  implementation must also look at the previous day's service.
- Sat 2026-11-14 09:00: T6 Sat, T6 Sun, then Monday's T1.
- Tue 2026-12-08 07:00 (holiday): T6 that day, then Wednesday's T1 and T2.
- Fri 2027-01-01 07:00: nothing (the feed ends 2026-12-31).
