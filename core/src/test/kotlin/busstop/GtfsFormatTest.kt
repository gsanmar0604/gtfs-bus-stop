package busstop

import java.time.LocalDateTime
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GtfsFormatTest {

    // Reusable base files; trips continue S1 → S2 so S1 is never the last stop.
    private val baseStops = "stop_id,stop_name\nS1,Test\nS2,Other\n"
    private val baseRoutes = "route_id,route_short_name\nR1,L1\n"
    private val baseTrips = "trip_id,route_id,service_id,trip_headsign\nT1,R1,SVC,Dest\n"
    private val baseStopTimes =
        "trip_id,departure_time,arrival_time,stop_id,stop_sequence\n" +
        "T1,09:00:00,09:00:00,S1,1\n" +
        "T1,09:10:00,09:10:00,S2,2\n"
    private val baseDates = "service_id,date,exception_type\nSVC,20261113,1\n"

    private fun writeBase(dir: java.nio.file.Path) {
        dir.resolve("stops.txt").writeText(baseStops)
        dir.resolve("trips.txt").writeText(baseTrips)
        dir.resolve("stop_times.txt").writeText(baseStopTimes)
        dir.resolve("calendar_dates.txt").writeText(baseDates)
    }

    @Test
    fun `missing route_short_name falls back to route_long_name`() {
        val dir = createTempDirectory()
        try {
            writeBase(dir)
            dir.resolve("routes.txt").writeText(
                "route_id,route_long_name\nR1,Express Line\n"
            )
            val departures = GtfsFeed.load(dir).nextDepartures("S1", LocalDateTime.parse("2026-11-13T08:00"))
            assertEquals(1, departures.size)
            assertEquals("Express Line", departures[0].line)
        } finally { dir.toFile().deleteRecursively() }
    }

    @Test
    fun `missing required column throws GtfsFormatException naming the file and column`() {
        val dir = createTempDirectory()
        try {
            writeBase(dir)
            dir.resolve("routes.txt").writeText(baseRoutes)
            // trips.txt is missing the trip_id column
            dir.resolve("trips.txt").writeText(
                "route_id,service_id,trip_headsign\nR1,SVC,Dest\n"
            )
            val e = assertFailsWith<GtfsFormatException> { GtfsFeed.load(dir) }
            assertTrue(e.message!!.contains("trips.txt"), "message should name the file")
            assertTrue(e.message!!.contains("trip_id"), "message should name the column")
        } finally { dir.toFile().deleteRecursively() }
    }

    @Test
    fun `short row throws GtfsFormatException for the missing column`() {
        val dir = createTempDirectory()
        try {
            writeBase(dir)
            dir.resolve("routes.txt").writeText(baseRoutes)
            // Header has 4 columns but the row only has 2 (service_id and beyond are missing)
            dir.resolve("trips.txt").writeText(
                "trip_id,route_id,service_id,trip_headsign\nT1,R1\n"
            )
            val e = assertFailsWith<GtfsFormatException> { GtfsFeed.load(dir) }
            assertTrue(e.message!!.contains("trips.txt"))
            assertTrue(e.message!!.contains("service_id"))
        } finally { dir.toFile().deleteRecursively() }
    }

    @Test
    fun `stop_times row with empty departure_time and no arrival_time is skipped`() {
        val dir = createTempDirectory()
        try {
            dir.resolve("stops.txt").writeText(baseStops)
            dir.resolve("routes.txt").writeText(baseRoutes)
            dir.resolve("trips.txt").writeText(
                "trip_id,route_id,service_id,trip_headsign\nT1,R1,SVC,Dest\nT2,R1,SVC,Dest\n"
            )
            dir.resolve("stop_times.txt").writeText(
                "trip_id,departure_time,arrival_time,stop_id,stop_sequence\n" +
                "T1,09:00:00,09:00:00,S1,1\n" +
                "T1,09:10:00,09:10:00,S2,2\n" +
                "T2,,,S1,1\n" +   // both times empty → skipped
                "T2,09:20:00,09:20:00,S2,2\n"
            )
            dir.resolve("calendar_dates.txt").writeText(baseDates)

            val departures = GtfsFeed.load(dir).nextDepartures("S1", LocalDateTime.parse("2026-11-13T08:00"))
            assertEquals(1, departures.size)
            assertEquals(LocalDateTime.parse("2026-11-13T09:00"), departures[0].time)
        } finally { dir.toFile().deleteRecursively() }
    }

    @Test
    fun `stop_times row with empty departure_time uses arrival_time as fallback`() {
        val dir = createTempDirectory()
        try {
            dir.resolve("stops.txt").writeText(baseStops)
            dir.resolve("routes.txt").writeText(baseRoutes)
            dir.resolve("trips.txt").writeText(
                "trip_id,route_id,service_id,trip_headsign\nT1,R1,SVC,Dest\nT2,R1,SVC,Dest\n"
            )
            dir.resolve("stop_times.txt").writeText(
                "trip_id,departure_time,arrival_time,stop_id,stop_sequence\n" +
                "T1,09:00:00,09:00:00,S1,1\n" +
                "T1,09:10:00,09:10:00,S2,2\n" +
                "T2,,09:30:00,S1,1\n" +   // departure_time empty, arrival_time present → use it
                "T2,09:40:00,09:40:00,S2,2\n"
            )
            dir.resolve("calendar_dates.txt").writeText(baseDates)

            val departures = GtfsFeed.load(dir).nextDepartures("S1", LocalDateTime.parse("2026-11-13T08:00"))
            assertEquals(2, departures.size)
            assertEquals(LocalDateTime.parse("2026-11-13T09:00"), departures[0].time)
            assertEquals(LocalDateTime.parse("2026-11-13T09:30"), departures[1].time)
        } finally { dir.toFile().deleteRecursively() }
    }
}
