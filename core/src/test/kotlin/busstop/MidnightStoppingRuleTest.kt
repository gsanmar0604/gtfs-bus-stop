package busstop

import java.time.LocalDateTime
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards against the midnight-stopping bug:
 * nextDepartures must keep checking the next service date even after it already
 * has `limit` candidates, because a 24:xx / 25:xx trip from the current day
 * maps to real Friday times and can be beaten by an ordinary Friday trip.
 *
 * Scenario (now = Thursday 23:00):
 *   Thursday service  → real times: 23:30, 00:30 (Fri), 01:10 (Fri)
 *   Friday service    → real times: 00:45 (Fri)
 *
 * Without the fix the loop exits as soon as it finds 3 candidates from Thursday,
 * returning [23:30, 00:30, 01:10].  Correct answer: [23:30, 00:30, 00:45].
 */
class MidnightStoppingRuleTest {

    private fun buildFeed(): java.nio.file.Path {
        val dir = createTempDirectory()

        dir.resolve("stops.txt").writeText(
            "stop_id,stop_name\nS1,Test Stop\nS2,Next Stop\n"
        )
        dir.resolve("routes.txt").writeText(
            "route_id,route_short_name\nR1,L1\n"
        )
        dir.resolve("trips.txt").writeText(
            "trip_id,route_id,service_id,trip_headsign\n" +
            "T_THU1,R1,THURSDAY,Dest\n" +
            "T_THU2,R1,THURSDAY,Dest\n" +
            "T_THU3,R1,THURSDAY,Dest\n" +
            "T_FRI1,R1,FRIDAY,Dest\n"
        )
        // Each trip continues to S2 so S1 is not the last stop (boarding-rules filter).
        dir.resolve("stop_times.txt").writeText(
            "trip_id,departure_time,arrival_time,stop_id,stop_sequence\n" +
            "T_THU1,23:30:00,23:30:00,S1,1\n" +   // Thu 23:30
            "T_THU1,23:40:00,23:40:00,S2,2\n" +
            "T_THU2,24:30:00,24:30:00,S1,1\n" +   // Fri 00:30
            "T_THU2,24:40:00,24:40:00,S2,2\n" +
            "T_THU3,25:10:00,25:10:00,S1,1\n" +   // Fri 01:10
            "T_THU3,25:20:00,25:20:00,S2,2\n" +
            "T_FRI1,00:45:00,00:45:00,S1,1\n" +   // Fri 00:45
            "T_FRI1,00:55:00,00:55:00,S2,2\n"
        )
        dir.resolve("calendar.txt").writeText(
            "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n"
        )
        dir.resolve("calendar_dates.txt").writeText(
            "service_id,date,exception_type\n" +
            "THURSDAY,20261112,1\n" +   // 2026-11-12 is a Thursday
            "FRIDAY,20261113,1\n"
        )

        return dir
    }

    @Test
    fun `Friday trip at 00h45 beats Thursday 25h10 trip in the top 3`() {
        val dir = buildFeed()
        try {
            val now = LocalDateTime.parse("2026-11-12T23:00")
            val departures = GtfsFeed.load(dir).nextDepartures("S1", now)

            assertEquals(3, departures.size)
            assertEquals(LocalDateTime.parse("2026-11-12T23:30"), departures[0].time)
            assertEquals(LocalDateTime.parse("2026-11-13T00:30"), departures[1].time)
            // 00:45 from Friday's service must beat 01:10 from Thursday's 25:10 trip.
            assertEquals(LocalDateTime.parse("2026-11-13T00:45"), departures[2].time)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
