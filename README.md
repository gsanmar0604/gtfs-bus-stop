# GTFS Bus Stop

Test task for Posimatiq: read a static [GTFS](https://gtfs.org/) dataset and show
the next departures for one hard-coded bus stop near Plaza Elíptica, Bilbao.

Language: Kotlin (Gradle, multi-module).

## Features

| Feature | Status |
|---|---|
| Basic GTFS Parsing (CLI, next 3 departures in Markdown) | Done |
| Static Bus Stop Info (Android UI) | Done |
| Dynamic Bus Stop Info (optional) | not started |
| Dynamic Bus Stop Info with Real-Time GTFS Updates (optional) | not started |

## Data

The real GTFS feed is not committed. Download the **Bizkaibus** dataset
from <https://data.ctb.eus/dataset> and place the zip in `data/` (git-ignored).

Unit tests and the Android demo use small hand-written fixtures in
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

- **Run on a device or emulator** via the green Run button.
- The app shows the next 3 departures for stop 4255 (MOYUA PLAZA 6) using the same
  parsing logic as the CLI — all GTFS code lives in `:core` with no Android dependencies.
- **To use the real Bizkaibus feed**, push the zip to the app's external files directory
  before launching:
  ```
  adb push data/bizkaibus.zip /sdcard/Android/data/busstop.app/files/
  ```
  Without the zip the app falls back to the bundled hand-written test fixtures.

## Project layout

```
core/   JVM library + CLI — GTFS parsing, departure logic, Markdown output
app/    Android application — Compose UI, reuses :core
```
