package busstop

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedSubsetTest {

    private val fixturesPath get() = Path.of(javaClass.getResource("/fixtures")!!.toURI())

    private fun makeSubset(source: Path, stopId: String): Path {
        val tmp = Files.createTempFile("subset", ".zip")
        writeStopSubset(source, stopId, tmp)
        return tmp
    }

    private fun sameResult(full: Path, subset: Path, stopId: String, now: LocalDateTime) {
        val fromFull = GtfsFeed.load(full, stopIds = setOf(stopId)).nextDepartures(stopId, now)
        val fromSubset = GtfsFeed.load(subset, stopIds = setOf(stopId)).nextDepartures(stopId, now)
        assertEquals(fromFull, fromSubset, "Results differ at $now")
    }

    @Test
    fun `subset of fixtures gives the same departures as the full fixtures`() {
        val subset = makeSubset(fixturesPath, "S1")
        try {
            listOf(
                LocalDateTime.parse("2026-11-12T07:00"),  // weekday morning
                LocalDateTime.parse("2026-11-12T23:59"),  // near midnight (T7 at 25:10 still ahead)
                LocalDateTime.parse("2026-11-13T00:30"),  // after midnight, T7 service date
                LocalDateTime.parse("2026-12-08T09:00"),  // holiday: WEEKDAY cancelled, WEEKEND added
                LocalDateTime.parse("2027-01-01T08:00"),  // after feed end → empty
            ).forEach { sameResult(fixturesPath, subset, "S1", it) }
        } finally {
            Files.deleteIfExists(subset)
        }
    }

    @Test
    fun `subset preserves the last-stop boarding rule`() {
        // The same feed as BoardingRulesTest. The key: T_LAST_STOP has two rows —
        // S2(seq1) and S1(seq2). Without the last-stop row in the subset, S1 would
        // look like seq1 == maxSeq(1), making T_LAST_STOP appear boardable.
        val dir = createTempDirectory()
        try {
            dir.resolve("stops.txt").writeText("stop_id,stop_name\nS1,Test Stop\nS2,Other\nS3,Further\n")
            dir.resolve("routes.txt").writeText("route_id,route_short_name\nR1,L1\n")
            dir.resolve("trips.txt").writeText(
                "trip_id,route_id,service_id,trip_headsign\n" +
                "T_NORMAL,R1,SVC,Dest\nT_NO_PICKUP,R1,SVC,Dest\n" +
                "T_LAST_STOP,R1,SVC,Dest\nT_CONTINUES,R1,SVC,Dest\n"
            )
            dir.resolve("stop_times.txt").writeText(
                "trip_id,departure_time,arrival_time,stop_id,stop_sequence,pickup_type\n" +
                "T_NORMAL,08:00:00,08:00:00,S1,1,0\n" +
                "T_NORMAL,08:10:00,08:10:00,S2,2,0\n" +
                "T_NO_PICKUP,08:30:00,08:30:00,S1,1,1\n" +
                "T_NO_PICKUP,08:40:00,08:40:00,S2,2,0\n" +
                "T_LAST_STOP,09:00:00,09:00:00,S2,1,0\n" +
                "T_LAST_STOP,09:10:00,09:10:00,S1,2,0\n" +  // S1 is the last stop
                "T_CONTINUES,09:30:00,09:30:00,S2,1,0\n" +
                "T_CONTINUES,09:40:00,09:40:00,S1,2,0\n" +
                "T_CONTINUES,09:50:00,09:50:00,S3,3,0\n"
            )
            dir.resolve("calendar_dates.txt").writeText("service_id,date,exception_type\nSVC,20261113,1\n")

            val now = LocalDateTime.parse("2026-11-13T07:00")
            val subset = makeSubset(dir, "S1")
            try {
                sameResult(dir, subset, "S1", now)
                val departures = GtfsFeed.load(subset, stopIds = setOf("S1")).nextDepartures("S1", now)
                assertEquals(2, departures.size, "Only T_NORMAL and T_CONTINUES should be boardable")
                assertEquals(LocalDateTime.parse("2026-11-13T08:00"), departures[0].time)
                assertEquals(LocalDateTime.parse("2026-11-13T09:40"), departures[1].time)
            } finally {
                Files.deleteIfExists(subset)
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
