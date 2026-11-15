# GTFS Bus Stop

[![Tests](https://github.com/gsanmar0604/gtfs-bus-stop/actions/workflows/tests.yml/badge.svg)](https://github.com/gsanmar0604/gtfs-bus-stop/actions/workflows/tests.yml)

Test task for Posimatiq: read a static [GTFS](https://gtfs.org/) dataset and show
the next departures for one hard-coded bus stop near Plaza Elíptica, Bilbao.
Plaza Moyúa (the stop's square) is the name locals use for Plaza Elíptica, so "MOYUA PLAZA 6" is the right stop.

Language: Kotlin (Gradle, multi-module).

## Features

| Feature | Status |
|---|---|
| Basic GTFS Parsing (CLI, next 3 departures in Markdown) | Done |
| Static Bus Stop Info (Android UI) | Done |
| Dynamic Bus Stop Info (optional) | Done |
| Dynamic Bus Stop Info with Real-Time GTFS Updates (optional) | not started |

## Data

The real GTFS feed is not committed. Download the **Bizkaibus** dataset
from <https://data.ctb.eus/dataset> and save the zip as `data/bizkaibus.zip` (git-ignored).

Unit tests use small hand-written fixtures in
`core/src/test/resources/fixtures/`.

## Build, test, run

### CLI (Feature 1)

```sh
# Run all unit tests
./gradlew :core:test

# Print next 3 departures for stop 4255 (MOYUA PLAZA 6) from a zip
./gradlew :core:run --args="data/bizkaibus.zip"

# Or from an already-unzipped directory
./gradlew :core:run --args="data"
```

On Windows use `.\gradlew` instead of `./gradlew`.

Sample output:

```markdown
> Task :core:run
## Next departures at MOYUA PLAZA 6 (4255)

| Time | Line | Destination |
|------|------|-------------|
| 16:00 | A3518 | BAKIO (Aut.) |
| 17:00 | A3527 | BERMEO |
| 18:00 | A3518 | BAKIO (Aut.) |

BUILD SUCCESSFUL in 3s
2 actionable tasks: 1 executed, 1 up-to-date
```

### Android app (Feature 2)

Open the project in Android Studio. The `:app` module is the Android application.

- **Run on a device or emulator** via the green Run button.
- **Data:** put the Bizkaibus zip at `data/bizkaibus.zip` before building. The build packs it
  into the app as an asset, so no extra setup is needed on the device. Without it, the app
  shows a message asking you to add the zip and rebuild.
- **Why bundled:** simplest to run, works offline, and the dataset still isn't committed.
  A production app would instead download the feed from CTB on first launch, cache it,
  and check for updates.
- The app shows the next 3 departures for stop 4255 (MOYUA PLAZA 6) using the same
  parsing logic as the CLI — all GTFS code lives in `:core` with no Android dependencies.

![App screenshot](docs/screenshot.png)

### How the live update works (Feature 3)

The app reuses the same `GtfsFeed` object that is loaded on startup and calls
`nextDepartures` once per minute, always at the next whole-minute boundary.
A departure at `08:00` is visible during the entire `08:00:xx` window and
disappears at the `08:01:00` tick — consistent with the `HH:mm` display.
The timing helper `delayUntilNextMinute(now)` is a pure function in `:core`
so it can be unit-tested without Android or coroutines.

The refresh loop runs only while the screen is visible. The `StateFlow` uses
`SharingStarted.WhileSubscribed(5_000)`, so putting the app in the background
stops the loop within 5 seconds. Returning to the app restarts the flow and
recomputes immediately — the display is never stale after the phone wakes.
A screen rotation reuses the cached `GtfsFeed` in the ViewModel, so there is
no second 8–10 s parse.

## Design notes

### Which stop times are shown

A stop time is skipped if `pickup_type = 1` (no passengers board) or if it is
the trip's last stop. Feeds don't always mark the last stop with `pickup_type = 1`,
so both conditions are checked. The check requires a full scan of `stop_times.txt`
to find each trip's highest `stop_sequence` before the per-stop filter is applied.

### GTFS times past midnight

GTFS departure times can exceed 24:00:00 (the real Bizkaibus feed has ~3,900 such
rows). A time of `25:10:00` on a Thursday service date is stored as Friday 01:10.
The search starts from `now.date - 1` to catch these late-night trips.

## Project layout

```
core/   JVM library + CLI — GTFS parsing, departure logic, Markdown output
app/    Android application — Compose UI, reuses :core
```
