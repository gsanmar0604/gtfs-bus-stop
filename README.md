# GTFS Bus Stop

Test task for Posimatiq: read a static [GTFS](https://gtfs.org/) dataset and show
the next departures for one hard-coded bus stop near Plaza Elíptica, Bilbao.

Language: Kotlin (Gradle).

## Features

| Feature | Status |
|---|---|
| Basic GTFS Parsing (CLI, next 3 departures in Markdown) | TODO |
| Static Bus Stop Info (graphical UI) | TODO |
| Dynamic Bus Stop Info (optional) | not started |
| Dynamic Bus Stop Info with Real-Time GTFS Updates (optional) | not started |

## Data

The real GTFS feed is not committed. Download the Bilbobus or Bizkaibus dataset
from <https://data.ctb.eus/dataset> and put it in `data/` (git-ignored).

Unit tests use small hand-written fixtures in `src/test/resources/fixtures/`.

## Build, test, run

```sh
gradle test
gradle run --args="data/<feed>.zip"
```

## Project layout

```
src/main/kotlin/busstop/       application code
src/test/kotlin/busstop/       unit tests
src/test/resources/fixtures/   tiny GTFS fixtures for tests
```
