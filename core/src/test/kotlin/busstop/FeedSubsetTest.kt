package busstop

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
        // The same feed as BoardingRulesTest. Without the last-stop rows in the subset,
        // T_NORMAL and T_CONTINUES would have only their S1 row, appear to end at S1,
        // and be wrongly hidden — producing 0 departures instead of 2.
        // Keeping each trip's last row prevents that.
        val dir = createTempDirectory()
        try {
            writeBoardingRulesFeed(dir)

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

    @Test
    fun `malformed stop_times throws GtfsFormatException naming the file and column`() {
        val dir = createTempDirectory()
        try {
            dir.resolve("stops.txt").writeText("stop_id,stop_name\nS1,Test\nS2,Other\n")
            dir.resolve("routes.txt").writeText("route_id,route_short_name\nR1,L1\n")
            dir.resolve("trips.txt").writeText("trip_id,route_id,service_id\nT1,R1,SVC\n")
            // stop_sequence column is missing
            dir.resolve("stop_times.txt").writeText(
                "trip_id,departure_time,stop_id\nT1,08:00:00,S1\n"
            )
            dir.resolve("calendar_dates.txt").writeText("service_id,date,exception_type\nSVC,20261113,1\n")
            val tmp = Files.createTempFile("subset", ".zip")
            try {
                val e = assertFailsWith<GtfsFormatException> { writeStopSubset(dir, "S1", tmp) }
                assertTrue(e.message!!.contains("stop_times.txt"), "message should name the file")
                assertTrue(e.message!!.contains("stop_sequence"), "message should name the column")
            } finally {
                Files.deleteIfExists(tmp)
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
