package busstop

import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile

private val GTFS_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd")

private fun parseGtfsDate(s: String): LocalDate = LocalDate.parse(s.trim(), GTFS_DATE_FMT)

private fun parseGtfsTime(s: String): Duration {
    val parts = s.trim().split(":")
    return Duration.ofHours(parts[0].toLong())
        .plusMinutes(parts[1].toLong())
        .plusSeconds(parts[2].toLong())
}

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
    private val feedEndDate: LocalDate,
) {

    fun stopName(stopId: String): String? = stops[stopId]

    fun nextDepartures(stopId: String, now: LocalDateTime, limit: Int = 3): List<Departure> {
        val times = stopTimes[stopId] ?: return emptyList()
        val candidates = mutableListOf<Departure>()

        // Start one day back: a 25h+ trip from yesterday's service day may still depart after now.
        var date = now.toLocalDate().minusDays(1)
        val cap = now.toLocalDate().plusDays(14)

        while (candidates.size < limit && !date.isAfter(feedEndDate) && !date.isAfter(cap)) {
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
            if (source.toString().endsWith(".zip"))
                loadFromZip(source, stopIds)
            else
                loadFromDirectory(source, stopIds)

        private fun loadFromDirectory(dir: Path, stopIds: Set<String>): GtfsFeed {
            fun open(name: String): Reader? {
                val p = dir.resolve(name)
                return if (Files.exists(p)) p.toFile().reader() else null
            }
            return build(::open, stopIds)
        }

        private fun loadFromZip(zipPath: Path, stopIds: Set<String>): GtfsFeed {
            val zip = ZipFile(zipPath.toFile())
            fun open(name: String): Reader? {
                val entry = zip.getEntry(name) ?: return null
                return zip.getInputStream(entry).bufferedReader()
            }
            return try { build(::open, stopIds) } finally { zip.close() }
        }

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
                parseCsv(r).associate { it["stop_id"]!! to it["stop_name"]!! }
            } ?: emptyMap()

        private fun loadRouteNames(open: (String) -> Reader?): Map<String, String> =
            open("routes.txt")?.use { r ->
                parseCsv(r).associate { it["route_id"]!! to it["route_short_name"]!! }
            } ?: emptyMap()

        private fun loadTrips(open: (String) -> Reader?, routeNames: Map<String, String>): Map<String, TripInfo> =
            open("trips.txt")?.use { r ->
                parseCsv(r).associate { row ->
                    row["trip_id"]!! to TripInfo(
                        serviceId = row["service_id"]!!,
                        line = routeNames[row["route_id"]!!] ?: row["route_id"]!!,
                        headsign = row["trip_headsign"] ?: "",
                    )
                }
            } ?: emptyMap()

        // Streams stop_times line by line to avoid loading all 780k rows into memory.
        private fun loadStopTimes(open: (String) -> Reader?, stopIds: Set<String>): Map<String, List<StopTime>> {
            val result = mutableMapOf<String, MutableList<StopTime>>()
            open("stop_times.txt")?.use { r ->
                for (row in parseCsv(r)) {
                    val stopId = row["stop_id"]!!
                    if (stopIds.isNotEmpty() && stopId !in stopIds) continue
                    result.getOrPut(stopId) { mutableListOf() }
                        .add(StopTime(row["trip_id"]!!, parseGtfsTime(row["departure_time"]!!)))
                }
            }
            return result
        }

        private fun loadCalendar(open: (String) -> Reader?): Map<String, ServiceRule> =
            open("calendar.txt")?.use { r ->
                parseCsv(r).associate { row ->
                    row["service_id"]!! to ServiceRule(
                        startDate = parseGtfsDate(row["start_date"]!!),
                        endDate = parseGtfsDate(row["end_date"]!!),
                        weekdays = buildSet {
                            if (row["monday"] == "1") add(DayOfWeek.MONDAY)
                            if (row["tuesday"] == "1") add(DayOfWeek.TUESDAY)
                            if (row["wednesday"] == "1") add(DayOfWeek.WEDNESDAY)
                            if (row["thursday"] == "1") add(DayOfWeek.THURSDAY)
                            if (row["friday"] == "1") add(DayOfWeek.FRIDAY)
                            if (row["saturday"] == "1") add(DayOfWeek.SATURDAY)
                            if (row["sunday"] == "1") add(DayOfWeek.SUNDAY)
                        },
                    )
                }
            } ?: emptyMap()

        private fun loadCalendarDates(open: (String) -> Reader?): Map<String, Map<LocalDate, Int>> {
            val result = mutableMapOf<String, MutableMap<LocalDate, Int>>()
            open("calendar_dates.txt")?.use { r ->
                for (row in parseCsv(r)) {
                    result.getOrPut(row["service_id"]!!) { mutableMapOf() }[parseGtfsDate(row["date"]!!)] =
                        row["exception_type"]!!.trim().toInt()
                }
            }
            return result
        }

        private fun computeFeedEndDate(
            calendar: Map<String, ServiceRule>,
            calendarDates: Map<String, Map<LocalDate, Int>>,
        ): LocalDate {
            val allDates = buildList {
                calendar.values.forEach { add(it.endDate) }
                calendarDates.values.forEach { addAll(it.keys) }
            }
            return allDates.maxOrNull() ?: LocalDate.MIN
        }
    }
}
