# Project context: GTFS Bus Stop (Kotlin)

Test task for a Trainee Software Engineer position at Posimatiq. A reviewer will
read this code and ask the author detailed questions about it in an interview, so
**keep the code simple, readable and easy to explain.** The author must be able to
justify every design choice. Prefer plain Kotlin and the standard library over clever
abstractions or extra dependencies.

The repository link (https://github.com/gsanmar0604/gtfs-bus-stop). `main` must always build and pass tests
(GitHub Actions runs them on every push).

## What the author built

A program that reads a **static GTFS** dataset and shows the **next 3 departures** from
one hard-coded bus stop near Plaza Elíptica (Plaza Moyúa), Bilbao. The task page says:
"use one hard-coded Target Stop: any bus stop near Plaza Elíptica de Bilbao". That is the
whole scope: **do not add other functionality** (stop selection, maps, search, etc.).

| Feature | Required | Status | Summary |
|---|---|---|---|
| 1. Basic GTFS Parsing | **Yes** | Done | CLI: parse a complete static GTFS dataset, pick the Target Stop, print the next 3 departures as **Markdown**. |
| 2. Static Bus Stop Info | **Yes** | Done | Android app (Jetpack Compose) reusing the feature 1 code to show the next 3 departures. |
| 3. Dynamic Bus Stop Info | Optional | Done | The Android screen updates itself at every minute boundary, with an "in X min" countdown, only while visible. |
| 4. Real-time updates (GTFS-rt) | Optional | Out of scope | Needs a Phoenix backend. Not planned. |

All three features work and were checked on the emulator.

### Decisions taken

- **GUI for feature 2:** Android app in a separate Gradle module (`:app`) that depends on `:core`.
  All parsing and departure logic stays in `:core`, which has **no UI or Android dependencies**.
- **How the app gets the feed:** Kirill said it's up to us. Decision: **bundled into the APK at build
  time**, as a one-stop extract written by `:core` (`writeStopSubset`). A production app would download
  the feed from CTB, cache it and check for updates.
- **Not doing:** ViewModel unit tests (would need Robolectric and `kotlinx-coroutines-test`); a `Clock`
  is injected instead. Interview answer: "the logic is tested in `:core`; the ViewModel would be tested
  with a test dispatcher and a fixed clock".
- **The CLI reads the complete dataset**, as the task requires; only the app uses the extract.
- **Coverage (2026-11-15):** measure it with **JaCoCo** (Gradle's built-in `jacoco` plugin; approved, no
  third-party plugin) for `:core` only, and show it on GitHub (job summary + downloadable HTML report;
  a badge only if the author wants an external service). Raise coverage only with **meaningful tests**:
  test the CLI entry points through `runCli` / `runSubset` (tasks 21–22). **Don't** exclude files from the
  report to raise the number, and don't chase branch coverage with tests for every `?:` fallback.
  The Android app is not unit-tested by design.
- **Errors:** a malformed feed must give a clear `GtfsFormatException` naming the file (and column or
  value) everywhere: CLI (stderr + exit 1), app (error screen), build (the extract step fails).
  Never a raw stack trace, never silently different data.

## Fixed parameters

- **Operator and feed:** Bizkaibus (published by Lantik). Valid **2026-11-09 to 2027-01-09**.
- **Data download:** `curl -L --create-dirs -o data/bizkaibus.zip https://ctb-gtfs.s3.eu-south-2.amazonaws.com/bizkaibus.zip`
  (in the README's quick start, with the dataset page as the fallback).
- **Target Stop:** `stop_id` **`4255`**, "MOYUA PLAZA 6". The real feed's `stop_name` prints as
  "MOYUA PLAZA 6 (4255)". The square's official name is Plaza Federico Moyúa; locals call it
  **Plaza Elíptica**. Defined once as `TARGET_STOP` in `core/src/main/kotlin/busstop/Config.kt`.
- **Timezone:** `MADRID_ZONE = "Europe/Madrid"`, also in `Config.kt`. Read the clock only at the edge
  of the program (`Main.kt`, the ViewModel's `Clock`); everything inside takes `now` as a parameter.
- **Real data:** not committed (about 120 MB unzipped, `stop_times.txt` has about 780,000 rows).
  Saved as `data/bizkaibus.zip` (git-ignored). Dataset source: https://data.ctb.eus/dataset

## Current state of the repo (`main`)

Gradle (Kotlin DSL) multi-module project, Kotlin 2.1.20, package `busstop`. Gradle wrapper committed and
executable. CI: `.github/workflows/tests.yml` runs `./gradlew :core:test` (Temurin 21) on every push and
pull request; badge in the README. CI does **not** build the app or the extract (it has no data).

```
core/   JVM library + CLI (jvmToolchain 21). No Android dependencies.
app/    Android app (AGP 8.5.2, compileSdk 35, minSdk 26, Jetpack Compose, Material 3).
data/   Real GTFS zip (git-ignored). Only used at build time.
docs/   screenshot.png (used in the README).
.github/workflows/tests.yml   CI.
```

### `:core` (`core/src/main/kotlin/busstop/`)

- `Config.kt`: `TARGET_STOP`, `MADRID_ZONE`.
- `Departure.kt`: `data class Departure(line, headsign, time: LocalDateTime)`; `time` is the real date and time.
- `Csv.kt`: `parseCsv(reader)` (lazy rows as maps; BOM, RFC 4180 quotes, trimmed headers, blank lines) and
  `internal fun splitCsvLine(line)`.
- `GtfsFormatException.kt`: `class GtfsFormatException(message)`.
- `GtfsSource.kt`: `internal fun <T> withGtfsFiles(source, block)`: opens a `.zip` or a directory, passes an
  `open(name): Reader?` function, closes the zip. Used by `GtfsFeed.load` and `writeStopSubset`.
- `GtfsFeed.kt`:
    - `parseGtfsDate(s, file)`, `parseGtfsTime(s, file)`, `parseGtfsInt(s, column, file)` (internal, top of the
      file): throw `GtfsFormatException` naming the file and the bad value.
    - `Map<String, String>.required(column, file)`: throws `GtfsFormatException("$file: missing column '$column'")`
      when absent or empty (covers short rows).
    - Optional columns with fallbacks: line label `route_short_name` → `route_long_name` → `route_id`;
      headsign → `""`; `pickup_type` → `0`; time `departure_time` → `arrival_time` (row skipped if neither).
    - `loadStopTimes`: **one pass**. Every row updates `maxSeqByTrip`; rows of wanted stops become candidates;
      after the pass, candidates with `pickup_type = 1` or `seq == maxSeqByTrip[trip]` are dropped
      (the **boarding rule**, which lives only here). Whole numbers go through `parseGtfsInt`.
    - `isActive`, `nextDepartures(stopId, now, limit = 3)` with the midnight stopping rule, `lastServiceDate`.
    - `expiredOn(now)`: `lastServiceDate` if today is after it, else `null`. Used by the CLI and the app.
    - The line-label fallback to `route_id` is at **line 133** (`?: routeId`): the only logic line not covered
      by a test (task 20).
- `FeedSubset.kt`: `writeStopSubset(source, stopId, target)`: for trips serving the stop, keeps the stop's
  row(s) **and each trip's last row** (otherwise every trip would look like it ends at the stop);
  `trips.txt` filtered; `stops`, `routes`, `calendar`, `calendar_dates`, `agency` copied unchanged; lines copied
  verbatim. Uses `required(...)`, so a missing column fails the build.
- `SubsetMain.kt`: `main(<source> <target>)` → `writeStopSubset(..., TARGET_STOP, ...)`.
- `MarkdownRenderer.kt`: `renderMarkdown(stopName, departures, expiredOn: LocalDate? = null)`. Shows the
  expired message **only when `departures` is empty**.
- `Refresh.kt`: `delayUntilNextMinute(now)`; `minutesUntil(departure, now)` (both times cut to whole minutes).
- `Main.kt`: CLI over the **complete** feed. Catches `GtfsFormatException` → stderr + exit 1. Uses `feed.expiredOn(now)`.

### Build

- `:core:buildFeedSubset` (`JavaExec`, `busstop.SubsetMainKt`): `data/bizkaibus.zip` →
  `core/build/gtfsSubset/bizkaibus.zip`. `inputs.file(sourceZip).optional()`, `outputs.file(targetZip)`,
  `onlyIf { sourceZip.exists() }`.
- `:app` `copyGtfsFeed` (`dependsOn(":core:buildFeedSubset")`, before `preBuild`) copies that extract into
  `app/build/generated/gtfsAssets`, an assets source dir. Asset name: `bizkaibus.zip`.

### `:app` (`app/src/main/kotlin/busstop/`)

- `BusStopViewModel.kt`: `@JvmOverloads constructor(app, clock: Clock = Clock.system(Madrid))`. `state` is a
  `flow { … }` that loads the feed once (cached in the ViewModel; asset copied to `cacheDir` because `ZipFile`
  needs a real file), then loops each minute: departures, `f.expiredOn(now)`, emit
  `FeedState.Ready`, `delay(delayUntilNextMinute(now))`. `.catch` → `FeedState.Error`;
  `.stateIn(viewModelScope, WhileSubscribed(5_000), Loading)`.
- `FeedState.kt`: `Loading`, `Ready(stopName, departures, updatedAt, expiredOn)`, `Error(message)`.
- `BusStopScreen.kt`: `collectAsStateWithLifecycle()`; stop name, "Loading timetable…", "Updated HH:mm",
  a `Card` with Time / Wait / Line / Destination. Shows the expired message **only when there are no departures**.
- **Rotation vs background:** a rotation takes less than 5 s, so `WhileSubscribed(5_000)` keeps the flow running
  and nothing restarts. The cached feed matters when coming back after more than 5 s in the background: the flow
  restarts, recalculates immediately, and doesn't reload. The ViewModel comment says this correctly.

### Tests (`core/src/test/kotlin/busstop/`), 53 total, all passing

| File | Tests | Covers |
|---|---|---|
| `NextDeparturesTest` | 13 | Order, past buses, "exactly now", limit, headsigns, next day, past 24:00, weekday/weekend, holidays, feed end, stop filter, unknown stop |
| `RefreshTest` | 8 | `minutesUntil`, `delayUntilNextMinute`, "moving time forward" story |
| `CsvTest` | 7 | BOM, quotes, escaped quotes, trimmed headers, blank lines, empty input |
| `GtfsFormatTest` | 9 | Missing required column, short row, `route_long_name` fallback, empty times, malformed times / dates / whole numbers |
| `MarkdownRendererTest` | 4 | Table format, after-midnight time, empty case, expired message |
| `FeedSubsetTest` | 3 | Extract = full fixtures at 5 times; extract keeps the boarding rule right; malformed `stop_times` fails |
| `CalendarEdgeCasesTest` | 2 | All service from `calendar_dates`; missing `calendar.txt` |
| `MidnightStoppingRuleTest` | 1 | Friday 00:45 beats Thursday's 25:10 in the top 3 |
| `BoardingRulesTest` | 1 | Normal kept; `pickup_type = 1` skipped; last stop skipped; continuing trip kept |
| `ExpiredFeedTest` | 4 | `lastServiceDate`; `expiredOn` before, on and after expiry; after-midnight bus still shown when expired |
| `GtfsZipTest` | 1 | Zip and directory give the same, non-empty result |

- `TestFeeds.kt`: shared test helpers (the boarding-rules feed used by two tests).
- `core/src/test/resources/fixtures/`: a tiny hand-written GTFS feed (target stop `S1`; every trip continues
  past `S1` to `S3`). **Read `fixtures/README.md` first.**
- `:app` has no unit tests, by decision.

### Coverage (measured locally, 2026-11-15; not yet in the repo)

| Part | Lines covered | Note |
|---|---|---|
| `:core` logic (`GtfsFeed`, `Csv`, `GtfsSource`, `FeedSubset`, `MarkdownRenderer`, `Refresh`, …) | 244 / 245 (99.6%) | Only miss: the `route_id` fallback (task 20) |
| `:core` entry points (`Main`, `SubsetMain`) | 0 / 28 | Read arguments and print; not unit-tested |
| `:app` (`BusStopScreen`, `BusStopViewModel`, `FeedState`, `MainActivity`) | 0 / 118 | UI; not unit-tested by design |
| **Whole project** | 244 / 391 (62%) | The local report included the app |
| **`:core` only** (what CI will report) | 244 / 273 (≈ 89%) | ≈ 99% after tasks 20–22 (only the one-line `main` wrappers left) |

Branch coverage of the logic: about 79% (185 / 234). Most partly covered branches are Kotlin `?:` / `?.`
fallbacks whose "missing data" side the test feeds never hit; that's expected.

**The tests define the behavior. Do not change existing tests or fixtures to fit the code** unless a test or
fixture is genuinely wrong, and then explain why to the author first. Prefer new self-contained test feeds.

## Next tasks (in this order)

### Task 19: show test coverage on GitHub

**Goal:** every CI run shows the `:core` coverage, with the full report downloadable. Approved: Gradle's built-in
`jacoco` plugin (no new third-party dependency).

1. **`core/build.gradle.kts`:**
   ```kotlin
   plugins {
       kotlin("jvm")
       application
       jacoco
   }

   tasks.test {
       useJUnitPlatform()
       finalizedBy(tasks.jacocoTestReport)   // always produce the report after the tests
   }

   tasks.jacocoTestReport {
       dependsOn(tasks.test)
       reports {
           xml.required = true    // read by CI for the summary
           html.required = true   // the human-readable report
       }
   }
   ```
   Locally: `./gradlew :core:test`, then open `core/build/reports/jacoco/test/html/index.html`.
   **Don't exclude `Main.kt` / `SubsetMain.kt`** to raise the number: report the honest ≈ 89% and explain it
   (the logic is ≈ 99.6%). If the author prefers excluding them, do it with a comment saying why.
2. **`.github/workflows/tests.yml`**, after the test step:
   ```yaml
         - run: ./gradlew :core:test

         - name: Coverage summary
           if: always()
           run: |
             python3 - <<'PY' >> "$GITHUB_STEP_SUMMARY"
             import xml.etree.ElementTree as ET
             root = ET.parse("core/build/reports/jacoco/test/jacocoTestReport.xml").getroot()
             print("## Test coverage (core)")
             for c in root.findall("counter"):
                 if c.get("type") in ("LINE", "BRANCH"):
                     missed, covered = int(c.get("missed")), int(c.get("covered"))
                     total = missed + covered
                     print(f"- {c.get('type').title()}: {100 * covered / total:.1f}% ({covered}/{total})")
             PY

         - name: Upload coverage report
           if: always()
           uses: actions/upload-artifact@v4
           with:
             name: coverage-report
             path: core/build/reports/jacoco/test/html
   ```
   The summary appears on the run's page in the **Actions** tab; the HTML report is under **Artifacts** at
   the bottom of that page. `if: always()` keeps the report even when a test fails.
3. **Optional, only if the author wants a coverage badge:** a free external service such as Codecov
   (`codecov/codecov-action`, needs a Codecov account and a `CODECOV_TOKEN` repository secret). Ask first:
   it's an extra account and a secret to manage.
4. **README:** in "Tests", one short paragraph: CI reports coverage for `core` (≈ 89% of lines, ≈ 79% of
   branches); the logic files are at ≈ 99.6% of lines; `Main`/`SubsetMain` and the Android app aren't
   unit-tested because they only read arguments, print, or display what `core` calculates. Use the numbers
   from the first CI run, not these estimates.
5. **Check:** push, open the run in the Actions tab, see the summary and download the artifact.

### Task 20: test the `route_id` fallback

One small test in `GtfsFormatTest`: a `routes.txt` with `route_id` but **neither** `route_short_name` nor
`route_long_name` (or both empty) → the departure's `line` is the `route_id`. Covers `GtfsFeed.kt` line 133,
the only uncovered logic line, and the README already describes this fallback. Follow similar structure to original
author-made tests.

### Task 21: make the CLI testable (`runCli`) and test it

**Why:** `Main.kt` is 0% covered because it can't be tested as written: it calls `exitProcess(...)`, which would
end the test run, reads the real clock, and writes straight to `System.out` / `System.err`. Yet its exit codes and
messages are Feature 1's interface, so far only checked by hand. Same idea as the app's injected `Clock`: keep side
effects (clock, console, exiting) at the very edge and pass them in.

**How:** move everything into a normal function that **returns** the exit code; `main` becomes a one-line wrapper.
```kotlin
/** The whole CLI, testable: returns the exit code instead of exiting. */
fun runCli(
    args: Array<String>,
    now: LocalDateTime,
    out: PrintStream,
    err: PrintStream,
    stopId: String = TARGET_STOP,
): Int {
    if (args.isEmpty()) {
        err.println("Usage: gtfs-bus-stop <path-to-gtfs-zip-or-directory>")
        return 1
    }
    // ... the same checks as today, with err.println(...) + return 1 instead of exitProcess(1)
    out.print(renderMarkdown(stopName, departures, feed.expiredOn(now)))
    return 0
}

fun main(args: Array<String>) {
    exitProcess(runCli(args, LocalDateTime.now(ZoneId.of(MADRID_ZONE)), System.out, System.err))
}
```
- `stopId` (default `TARGET_STOP`) and `now` let tests use the fixtures (stop `S1`) at a fixed time.
- Behaviour, messages and exit codes stay exactly as today. Comment the why on `runCli`.

**Tests first, new `MainTest`** (capture output with `ByteArrayOutputStream` + `PrintStream`):
- no argument → returns 1; `err` contains the usage line; `out` empty;
- path doesn't exist → 1; "path not found";
- stop not in the feed (e.g. `stopId = "NOPE"`) → 1; "not found in feed";
- malformed feed (a temp feed with a bad time, like `GtfsFormatTest`) → 1; the `GtfsFormatException` message;
- success with the fixtures at `2026-11-12T07:00` → 0; `out` equals the expected Markdown (08:00, 08:15, 08:30);
  `err` empty;
- optional: expired feed (`2027-01-01T08:00`) → 0; the "Timetable expired on 2026-12-31" message.

### Task 22: the same for `SubsetMain` (`runSubset`)

- `fun runSubset(args: Array<String>, err: PrintStream, stopId: String = TARGET_STOP): Int`: wrong argument
  count → usage + 1; missing source → "source not found" + 1; otherwise `writeStopSubset(...)`, print
  "Written: <target>", return 0. A `GtfsFormatException` should still **fail the build**: either let it propagate
  (the `JavaExec` task fails with the stack trace) or catch it, print the message to `err` and return 1, which also
  fails the build but with a cleaner message. Pick the second; it matches the CLI.
- `fun main(args)` = `exitProcess(runSubset(args, System.err))`.
- **Tests first, new `SubsetMainTest`:** wrong argument count → 1; missing source → 1; malformed feed → 1 with the
  message; success with the fixtures → 0 and the target zip exists (its content is already checked by
  `FeedSubsetTest`).
- Check `./gradlew :app:assembleDebug` still builds the extract (the Gradle task calls the same `main`).

After tasks 20–22, update the README's test table (new `MainTest`, `SubsetMainTest`) and the coverage paragraph
with the **real** numbers from CI. Interview line: "the CLI logic is in `runCli`, which takes the clock and the
output streams as parameters and returns the exit code, so it's unit-tested; `main` only wires in the real ones."

## Required behavior (reference)

### Which departures count

1. A trip departs a stop at `departure_time` of its `stop_times.txt` row for that stop (else `arrival_time`).
2. A trip runs on a **service date** if its `service_id` is active that date: `calendar.txt` weekday flag + date
   range, overridden by `calendar_dates.txt` (`1` adds, `2` removes).
3. **GTFS times can pass 24:00:00**. Real departure = service date at 00:00 + that duration.
4. **Check service dates from `now.date - 1` onward**, not only today.
5. Keep departures with real time **>= now**, sort by real time, return the first `limit`.
6. Keep walking dates until the next date's midnight is not before the `limit`-th best candidate, the feed's last
   service date is passed, or 14 days.
7. Unknown stop: empty list from `nextDepartures`, `null` from `stopName`.
8. Skip stop times with `pickup_type = 1` and stop times that are their trip's last stop.
9. The app's extract must give **exactly** the same departures as the full feed (`FeedSubsetTest`).
10. The expired message is shown only when there are no departures **and** today is after the last service date.

### The real feed has these traits (handled)

- **`calendar.txt` has all weekday flags set to 0.** All service comes from `calendar_dates.txt`.
- **Times include seconds** (`08:35:02`). **Trip IDs are long strings**; never parse as numbers.
- `stop_times.txt` is big: **stream it**. Files may start with a **UTF-8 BOM**; fields may be **quoted with commas**.
- Ignore unneeded files: `shapes.txt` (39 MB), `fare_*`, `levels.txt`, `feed_info.txt`.

### Markdown output (defined by `MarkdownRendererTest`)

```markdown
## Next departures at MOYUA PLAZA 6 (4255)

| Time | Line | Destination |
|------|------|-------------|
| 16:00 | A3518 | BAKIO (Aut.) |
```

Real clock time `HH:mm`; `No upcoming departures.` when empty, or `Timetable expired on <date>. Download a newer
feed.` when empty and expired; ends with a newline.

### Live updates (feature 3)

- Feed loaded once and cached in the ViewModel; departures recomputed at each whole minute.
- "in X min" = difference of the two HH:mm times; "now" when 0.
- Runs only while the screen is visible (`WhileSubscribed(5_000)` + `collectAsStateWithLifecycle`); recomputes
  immediately on return; rotation (< 5 s) doesn't restart it.

## Working agreements

- **TDD:** failing test first, then the smallest implementation that passes, then refactor.
- Run `./gradlew :core:test` after each change. All tests must pass before committing; check CI after pushing.
- **Small commits** with clear messages that tell the story (for example "Report malformed GTFS values clearly").
  End commit messages with the attribution lines required by the environment. Apply patches with `git am`.
- `git pull` before starting work; push after committing. Delete merged branches.
- Do not commit `data/`, zips, `local.properties`, or build output (see `.gitignore`).
- On Windows, set the executable bit with `git update-index --chmod=+x <file>`.
- No new dependencies without asking.
- Comment the **why** for non-obvious logic (midnight rollover, exception precedence, stopping rule, boarding
  rules, single-pass filtering, extract last-row trap, expired-vs-departures, lifecycle-aware collection), not the
  what. Keep comments and code consistent: a wrong comment is worse than none.
- Keep `README.md` in step with the code; only measured numbers.
- When something is ambiguous, ask the author instead of guessing.

## Done criteria

### Done
- [x] Features 1–3 checked on the emulator; review rounds 1 and 2; start-up (one pass + extract).
- [x] Tasks 16–18: README rewrite (direct download link, measured 8–10 s → ≈ 0.8 s); clear errors for malformed
  values; `expiredOn` in `GtfsFeed`; ViewModel comment fixed.
- [x] All unit tests pass (53); CI green with badge.

### Still open
- [ ] Task 19: JaCoCo in `:core`; CI shows the coverage summary and uploads the HTML report; README paragraph with
  the real CI numbers.
- [ ] Task 20: `route_id` fallback test.
- [ ] Task 21: `runCli` + `MainTest` (usage, missing path, unknown stop, malformed feed, success, expired);
  `main` is a one-line wrapper; behaviour unchanged.
- [ ] Task 22: `runSubset` + `SubsetMainTest`; `:app:assembleDebug` still builds the extract.
- [ ] README test table and coverage paragraph updated with the real CI numbers (≈ 99% of `:core` lines expected).
- [ ] Hand check: `./gradlew clean :app:assembleDebug` without `data/bizkaibus.zip` succeeds and the app shows
  "GTFS feed not bundled"; with the zip, departures appear.
- [ ] Check the direct download link gives the same feed (run the CLI on it).
- [ ] CLI run on the real zip; one departure spot-checked by hand against `stop_times.txt`, `trips.txt`,
  `calendar_dates.txt` and `routes.txt`.