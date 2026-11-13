package busstop

import java.time.LocalDateTime
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the boarding-rules filter applied at feed load time:
 *   - pickup_type = 1 (no pickup at this stop) → skipped
 *   - last stop of its trip → skipped even without pickup_type = 1
 *   - normal stop the trip continues past → kept
 */
class BoardingRulesTest {

    @Test
    fun `only boardable stop times appear in next departures`() {
        val dir = createTempDirectory()
        try {
            dir.resolve("stops.txt").writeText(
                "stop_id,stop_name\nS1,Test Stop\nS2,Other\nS3,Further\n"
            )
            dir.resolve("routes.txt").writeText(
                "route_id,route_short_name\nR1,L1\n"
            )
            dir.resolve("trips.txt").writeText(
                "trip_id,route_id,service_id,trip_headsign\n" +
                "T_NORMAL,R1,SVC,Dest\n" +
                "T_NO_PICKUP,R1,SVC,Dest\n" +
                "T_LAST_STOP,R1,SVC,Dest\n" +
                "T_CONTINUES,R1,SVC,Dest\n"
            )
            dir.resolve("stop_times.txt").writeText(
                "trip_id,departure_time,arrival_time,stop_id,stop_sequence,pickup_type\n" +
                // T_NORMAL: S1 first, continues to S2 → boardable
                "T_NORMAL,08:00:00,08:00:00,S1,1,0\n" +
                "T_NORMAL,08:10:00,08:10:00,S2,2,0\n" +
                // T_NO_PICKUP: pickup_type=1 at S1 → not boardable
                "T_NO_PICKUP,08:30:00,08:30:00,S1,1,1\n" +
                "T_NO_PICKUP,08:40:00,08:40:00,S2,2,0\n" +
                // T_LAST_STOP: S1 is the highest stop_sequence → not boardable
                "T_LAST_STOP,09:00:00,09:00:00,S2,1,0\n" +
                "T_LAST_STOP,09:10:00,09:10:00,S1,2,0\n" +
                // T_CONTINUES: S2 → S1 → S3; S1 is not the last stop → boardable
                "T_CONTINUES,09:30:00,09:30:00,S2,1,0\n" +
                "T_CONTINUES,09:40:00,09:40:00,S1,2,0\n" +
                "T_CONTINUES,09:50:00,09:50:00,S3,3,0\n"
            )
            dir.resolve("calendar_dates.txt").writeText(
                "service_id,date,exception_type\nSVC,20261113,1\n"
            )

            val now = LocalDateTime.parse("2026-11-13T07:00")
            val departures = GtfsFeed.load(dir, stopIds = setOf("S1")).nextDepartures("S1", now)

            // Only T_NORMAL (08:00) and T_CONTINUES (09:40) are boardable at S1.
            assertEquals(2, departures.size)
            assertEquals(LocalDateTime.parse("2026-11-13T08:00"), departures[0].time)
            assertEquals(LocalDateTime.parse("2026-11-13T09:40"), departures[1].time)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
