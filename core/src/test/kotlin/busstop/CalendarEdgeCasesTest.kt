package busstop

import java.time.LocalDateTime
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves that the feed works when all service comes from calendar_dates.txt —
 * the pattern used by the real Bizkaibus feed, where every weekday flag in
 * calendar.txt is 0 and all 439 active dates are calendar_dates exceptions.
 */
class CalendarEdgeCasesTest {

    // Minimal GTFS files shared across both tests.
    private val stops    = "stop_id,stop_name\nS1,Test Stop\n"
    private val routes   = "route_id,route_short_name\nR1,L1\n"
    private val trips    = "trip_id,route_id,service_id,trip_headsign\nT1,R1,SVC1,Dest\n"
    private val stopTimes = "trip_id,departure_time,arrival_time,stop_id,stop_sequence\nT1,09:00:00,09:00:00,S1,1\n"
    // SVC1 added only on 2026-11-13 (a Thursday in the fixture date range).
    private val calendarDates = "service_id,date,exception_type\nSVC1,20261113,1\n"

    @Test
    fun `service runs when calendar has all weekday flags set to 0 and calendar_dates adds it`() {
        // Real Bizkaibus pattern: calendar.txt is present but every flag is 0.
        // All actual service must come from calendar_dates exception_type = 1.
        val dir = createTempDirectory()
        try {
            dir.resolve("stops.txt").writeText(stops)
            dir.resolve("routes.txt").writeText(routes)
            dir.resolve("trips.txt").writeText(trips)
            dir.resolve("stop_times.txt").writeText(stopTimes)
            dir.resolve("calendar.txt").writeText(
                "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n" +
                "SVC1,0,0,0,0,0,0,0,20261101,20261231\n"
            )
            dir.resolve("calendar_dates.txt").writeText(calendarDates)

            val departures = GtfsFeed.load(dir).nextDepartures("S1", LocalDateTime.parse("2026-11-13T08:00"))

            assertEquals(1, departures.size)
            assertEquals(LocalDateTime.parse("2026-11-13T09:00"), departures[0].time)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `service runs when calendar_txt is absent and calendar_dates adds it`() {
        // KBus pattern: no calendar.txt at all; all service from calendar_dates.
        val dir = createTempDirectory()
        try {
            dir.resolve("stops.txt").writeText(stops)
            dir.resolve("routes.txt").writeText(routes)
            dir.resolve("trips.txt").writeText(trips)
            dir.resolve("stop_times.txt").writeText(stopTimes)
            // No calendar.txt written.
            dir.resolve("calendar_dates.txt").writeText(calendarDates)

            val departures = GtfsFeed.load(dir).nextDepartures("S1", LocalDateTime.parse("2026-11-13T08:00"))

            assertEquals(1, departures.size)
            assertEquals(LocalDateTime.parse("2026-11-13T09:00"), departures[0].time)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
