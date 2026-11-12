# GTFS Bus Stop

Test task for Posimatiq: read a static [GTFS](https://gtfs.org/) dataset and show
the next departures for one hard-coded bus stop near Plaza Elíptica, Bilbao.

Language: Kotlin (Gradle).

## Features

| Feature | Status |
|---|---|
| Basic GTFS Parsing (CLI, next 3 departures in Markdown) | Done |
| Static Bus Stop Info (graphical UI) | TODO |
| Dynamic Bus Stop Info (optional) | not started |
| Dynamic Bus Stop Info with Real-Time GTFS Updates (optional) | not started |

## Data

The real GTFS feed is not committed. Download the **Bizkaibus** dataset
from <https://data.ctb.eus/dataset> and unzip it into `data/` (git-ignored).

Unit tests use small hand-written fixtures in `src/test/resources/fixtures/`.

## Build, test, run

```sh
# Run tests
./gradlew test

# Print next 3 departures for stop 4255 (MOYUA PLAZA 6) from a zip
./gradlew run --args="data/bizkaibus.zip"

# Or from an already-unzipped directory
./gradlew run --args="data"
```

On Windows use `.\gradlew` instead of `./gradlew`.

## Project layout

```
src/main/kotlin/busstop/       application code
src/test/kotlin/busstop/       unit tests
src/test/resources/fixtures/   tiny GTFS fixtures for tests
```
