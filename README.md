# GTFS Bus Stop

Test task for Posimatiq: read a static [GTFS](https://gtfs.org/) dataset and show
the next departures for one hard-coded bus stop near Plaza Elíptica, Bilbao.

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

### Android app (Feature 2)

Open the project in Android Studio. The `:app` module is the Android application.

- **Data:** put the Bizkaibus zip at `data/bizkaibus.zip` before building. The build packs it
  into the app as an asset, so no extra setup is needed on the device. Without it, the app
  shows a message asking you to add the zip and rebuild.
- **Why bundled:** simplest to run, works offline, and the dataset still isn't committed.
  A production app would instead download the feed from CTB on first launch, cache it,
  and check for updates.
- The app shows the next 3 departures for stop 4255 (MOYUA PLAZA 6) using the same
  parsing logic as the CLI — all GTFS code lives in `:core` with no Android dependencies.

### How the live update works (Feature 3)

The app reuses the same `GtfsFeed` object that is loaded on startup and calls
`nextDepartures` once per minute, always at the next whole-minute boundary.
A departure at `08:00` is visible during the entire `08:00:xx` window and
disappears at the `08:01:00` tick — consistent with the `HH:mm` display.
The timing helper `delayUntilNextMinute(now)` is a pure function in `:core`
so it can be unit-tested without Android or coroutines.

## Project layout

```
core/   JVM library + CLI — GTFS parsing, departure logic, Markdown output
app/    Android application — Compose UI, reuses :core
```
