package busstop

import java.io.Reader
import java.nio.file.Path
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val GTFS_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd")

internal fun parseGtfsDate(s: String, file: String): LocalDate =
    try { LocalDate.parse(s.trim(), GTFS_DATE_FMT) }
    catch (_: DateTimeParseException) { throw GtfsFormatException("$file: invalid date '${s.trim()}'") }

internal fun parseGtfsTime(s: String, file: String): Duration {
    val parts = s.trim().split(":")
    if (parts.size != 3) throw GtfsFormatException("$file: invalid time '${s.trim()}'")
    return Duration.ofHours(parts[0].toLong())
        .plusMinutes(parts[1].toLong())
        .plusSeconds(parts[2].toLong())
}

internal fun parseGtfsInt(s: String, column: String, file: String): Int =
    s.trim().toIntOrNull() ?: throw GtfsFormatException("$file: invalid integer for '$column': '${s.trim()}'")

// Throws GtfsFormatException if the column is absent or empty (covers short rows too).
internal fun Map<String, String>.required(column: String, file: String): String =
    this[column]?.takeIf { it.isNotEmpty() }
        ?: throw GtfsFormatException("$file: missing column '$column'")

private data class ServiceRule(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val weekdays: Set<DayOfWeek>,
) {
    fun isActiveOn(date: LocalDate): Boolean =
        !date.isBefore(startDate) && !date.isAfter(endDate) && date.dayOfWeek in weekdays
}

private data class TripInfo(val serviceId: String, val line: String, val headsign: String)
private data class StopTime(val tripId: String, val departureDuration: Duration)

/** A parsed static GTFS dataset. */
class GtfsFeed private constructor(
    private val stops: Map<String, String>,
    private val trips: Map<String, TripInfo>,
    private val stopTimes: Map<String, List<StopTime>>,
    private val calendar: Map<String, ServiceRule>,
    private val calendarDates: Map<String, Map<LocalDate, Int>>,
    val lastServiceDate: LocalDate?,   // null when the feed has no calendar data at all
) {

    fun stopName(stopId: String): String? = stops[stopId]

    /** Returns [lastServiceDate] when [now] is past it, null otherwise (including during the feed's validity). */
    fun expiredOn(now: LocalDateTime): LocalDate? =
        lastServiceDate?.takeIf { now.toLocalDate().isAfter(it) }

    fun nextDepartures(stopId: String, now: LocalDateTime, limit: Int = 3): List<Departure> {
        val times = stopTimes[stopId] ?: return emptyList()
        val endDate = lastServiceDate ?: return emptyList()
        val candidates = mutableListOf<Departure>()

        // Start one day back: a 25h+ trip from yesterday's service day may still depart after now.
        var date = now.toLocalDate().minusDays(1)
        val cap = now.toLocalDate().plusDays(14)

        while (!date.isAfter(endDate) && !date.isAfter(cap)) {
            // A service day cannot produce a real time earlier than its own midnight.
            // Once we have `limit` candidates and this day's midnight is not before
            // the worst of them, nothing from this day or later can improve the answer.
            if (candidates.size >= limit) {
                val worstSoFar = candidates.sortedBy { it.time }[limit - 1].time
                if (!date.atStartOfDay().isBefore(worstSoFar)) break
            }
            for (st in times) {
                val trip = trips[st.tripId] ?: continue
                if (!isActive(trip.serviceId, date)) continue
                val realTime = date.atStartOfDay().plus(st.departureDuration)
                if (realTime >= now) candidates.add(Departure(trip.line, trip.headsign, realTime))
            }
            date = date.plusDays(1)
        }

        return candidates.sortedBy { it.time }.take(limit)
    }

    // calendar_dates exceptions take precedence over calendar base rules.
    private fun isActive(serviceId: String, date: LocalDate): Boolean =
        when (calendarDates[serviceId]?.get(date)) {
            2 -> false
            1 -> true
            else -> calendar[serviceId]?.isActiveOn(date) ?: false
        }

    companion object {

        /**
         * Loads a feed from a directory or zip file containing the GTFS .txt files.
         * Pass [stopIds] to keep only stop_times rows for those stops (saves memory on large feeds).
         */
        fun load(source: Path, stopIds: Set<String> = emptySet()): GtfsFeed =
            withGtfsFiles(source) { open -> build(open, stopIds) }

        private fun build(open: (String) -> Reader?, stopIds: Set<String>): GtfsFeed {
            val stops = loadStops(open)
            val routeNames = loadRouteNames(open)
            val trips = loadTrips(open, routeNames)
            val stopTimes = loadStopTimes(open, stopIds)
            val calendar = loadCalendar(open)
            val calendarDates = loadCalendarDates(open)
            val feedEndDate = computeFeedEndDate(calendar, calendarDates)
            return GtfsFeed(stops, trips, stopTimes, calendar, calendarDates, feedEndDate)
        }

        private fun loadStops(open: (String) -> Reader?): Map<String, String> =
            open("stops.txt")?.use { r ->
                parseCsv(r).associate { row ->
                    row.required("stop_id", "stops.txt") to row.required("stop_name", "stops.txt")
                }
            } ?: emptyMap()

        private fun loadRouteNames(open: (String) -> Reader?): Map<String, String> =
            open("routes.txt")?.use { r ->
                parseCsv(r).associate { row ->
                    val routeId = row.required("route_id", "routes.txt")
                    // GTFS requires route_short_name or route_long_name (not necessarily both).
                    val name = row["route_short_name"]?.takeIf { it.isNotEmpty() }
                        ?: row["route_long_name"]?.takeIf { it.isNotEmpty() }
                        ?: routeId
                    routeId to name
                }
            } ?: emptyMap()

        private fun loadTrips(open: (String) -> Reader?, routeNames: Map<String, String>): Map<String, TripInfo> =
            open("trips.txt")?.use { r ->
                parseCsv(r).associate { row ->
                    val routeId = row.required("route_id", "trips.txt")
                    row.required("trip_id", "trips.txt") to TripInfo(
                        serviceId = row.required("service_id", "trips.txt"),
                        line = routeNames[routeId] ?: routeId,
                        headsign = row["trip_headsign"] ?: "",
                    )
                }
            } ?: emptyMap()

        // Single pass over stop_times.txt: track the highest stop_sequence for every trip
        // while collecting candidate rows for the requested stops. The last-stop boarding
        // rule needs data from the whole file (a later row for a different stop proves a
        // trip continues), so it can only be applied after the scan is complete.
        private fun loadStopTimes(open: (String) -> Reader?, stopIds: Set<String>): Map<String, List<StopTime>> {
            data class Candidate(
                val stopId: String,
                val tripId: String,
                val seq: Int,
                val pickupType: Int,
                val time: Duration,
            )

            val maxSeqByTrip = mutableMapOf<String, Int>()
            val candidates = mutableListOf<Candidate>()

            open("stop_times.txt")?.use { r ->
                for (row in parseCsv(r)) {
                    val tripId = row.required("trip_id", "stop_times.txt")
                    val seq = parseGtfsInt(row.required("stop_sequence", "stop_times.txt"), "stop_sequence", "stop_times.txt")
                    if (seq > (maxSeqByTrip[tripId] ?: 0)) maxSeqByTrip[tripId] = seq

                    val stopId = row.required("stop_id", "stop_times.txt")
                    if (stopIds.isNotEmpty() && stopId !in stopIds) continue
                    // GTFS allows empty departure_time at non-timepoint stops; fall back to arrival_time.
                    val timeStr = row["departure_time"]?.takeIf { it.isNotEmpty() }
                        ?: row["arrival_time"]?.takeIf { it.isNotEmpty() }
                        ?: continue
                    val pickupType = row["pickup_type"]?.trim()?.toIntOrNull() ?: 0
                    candidates.add(Candidate(stopId, tripId, seq, pickupType, parseGtfsTime(timeStr, "stop_times.txt")))
                }
            }

            // Apply boarding rules now that maxSeqByTrip is complete.
            // Skip pickup_type=1 (no boarding) and the trip's last stop (feeds don't always
            // mark it with pickup_type=1; checking both is required by GTFS practice).
            val result = mutableMapOf<String, MutableList<StopTime>>()
            for (c in candidates) {
                if (c.pickupType == 1 || c.seq == maxSeqByTrip[c.tripId]) continue
                result.getOrPut(c.stopId) { mutableListOf() }.add(StopTime(c.tripId, c.time))
            }
            return result
        }

        private fun loadCalendar(open: (String) -> Reader?): Map<String, ServiceRule> =
            open("calendar.txt")?.use { r ->
                parseCsv(r).associate { row ->
                    row.required("service_id", "calendar.txt") to ServiceRule(
                        startDate = parseGtfsDate(row.required("start_date", "calendar.txt"), "calendar.txt"),
                        endDate = parseGtfsDate(row.required("end_date", "calendar.txt"), "calendar.txt"),
                        weekdays = buildSet {
                            if (row.required("monday",    "calendar.txt") == "1") add(DayOfWeek.MONDAY)
                            if (row.required("tuesday",   "calendar.txt") == "1") add(DayOfWeek.TUESDAY)
                            if (row.required("wednesday", "calendar.txt") == "1") add(DayOfWeek.WEDNESDAY)
                            if (row.required("thursday",  "calendar.txt") == "1") add(DayOfWeek.THURSDAY)
                            if (row.required("friday",    "calendar.txt") == "1") add(DayOfWeek.FRIDAY)
                            if (row.required("saturday",  "calendar.txt") == "1") add(DayOfWeek.SATURDAY)
                            if (row.required("sunday",    "calendar.txt") == "1") add(DayOfWeek.SUNDAY)
                        },
                    )
                }
            } ?: emptyMap()

        private fun loadCalendarDates(open: (String) -> Reader?): Map<String, Map<LocalDate, Int>> {
            val result = mutableMapOf<String, MutableMap<LocalDate, Int>>()
            open("calendar_dates.txt")?.use { r ->
                for (row in parseCsv(r)) {
                    val dateMap = result.getOrPut(row.required("service_id", "calendar_dates.txt")) { mutableMapOf() }
                    dateMap[parseGtfsDate(row.required("date", "calendar_dates.txt"), "calendar_dates.txt")] =
                        parseGtfsInt(row.required("exception_type", "calendar_dates.txt"), "exception_type", "calendar_dates.txt")
                }
            }
            return result
        }

        private fun computeFeedEndDate(
            calendar: Map<String, ServiceRule>,
            calendarDates: Map<String, Map<LocalDate, Int>>,
        ): LocalDate? {
            val allDates = buildList {
                calendar.values.forEach { add(it.endDate) }
                calendarDates.values.forEach { addAll(it.keys) }
            }
            return allDates.maxOrNull()
        }
    }
}
