package busstop

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MainTest {

    private val fixturesPath get() = Path.of(javaClass.getResource("/fixtures")!!.toURI())

    private fun run(
        args: Array<String>,
        now: LocalDateTime = LocalDateTime.parse("2026-11-12T07:00"),
        stopId: String = "S1",
    ): Triple<Int, String, String> {
        val outBuf = ByteArrayOutputStream()
        val errBuf = ByteArrayOutputStream()
        val exitCode = runCli(args, now, PrintStream(outBuf), PrintStream(errBuf), stopId)
        return Triple(exitCode, outBuf.toString(Charsets.UTF_8), errBuf.toString(Charsets.UTF_8))
    }

    @Test
    fun `no arguments prints usage and returns 1`() {
        val (code, out, err) = run(emptyArray())
        assertEquals(1, code)
        assertTrue(err.contains("Usage"), "err should contain 'Usage'")
        assertTrue(out.isEmpty(), "stdout should be empty")
    }

    @Test
    fun `missing path returns 1 with error message`() {
        val (code, _, err) = run(arrayOf("/no/such/path/feed.zip"))
        assertEquals(1, code)
        assertTrue(err.contains("not found"), "err should mention 'not found'")
    }

    @Test
    fun `unknown stop returns 1 with error message`() {
        val (code, _, err) = run(arrayOf(fixturesPath.toString()), stopId = "NOPE")
        assertEquals(1, code)
        assertTrue(err.contains("not found"), "err should mention 'not found'")
    }

    @Test
    fun `malformed feed returns 1 with GtfsFormatException message`() {
        val dir = createTempDirectory()
        try {
            dir.resolve("stops.txt").writeText("stop_id,stop_name\nS1,Test\nS2,Other\n")
            dir.resolve("routes.txt").writeText("route_id,route_short_name\nR1,L1\n")
            dir.resolve("trips.txt").writeText("trip_id,route_id,service_id\nT1,R1,SVC\n")
            dir.resolve("stop_times.txt").writeText(
                "trip_id,departure_time,arrival_time,stop_id,stop_sequence\n" +
                "T1,BADTIME,BADTIME,S1,1\nT1,09:10:00,09:10:00,S2,2\n"
            )
            dir.resolve("calendar_dates.txt").writeText("service_id,date,exception_type\nSVC,20261113,1\n")
            val (code, _, err) = run(arrayOf(dir.toString()))
            assertEquals(1, code)
            assertTrue(err.contains("stop_times.txt"), "err should name the file")
        } finally { dir.toFile().deleteRecursively() }
    }

    @Test
    fun `success with fixtures at 07 00 returns 0 and correct Markdown`() {
        val (code, out, err) = run(arrayOf(fixturesPath.toString()))
        assertEquals(0, code)
        assertTrue(err.isEmpty(), "stderr should be empty")
        assertTrue(out.contains("Plaza Eliptica"), "output should contain stop name")
        assertTrue(out.contains("08:00"), "output should contain first departure")
        assertTrue(out.contains("08:15"), "output should contain second departure")
        assertTrue(out.contains("08:30"), "output should contain third departure")
    }

    @Test
    fun `expired feed returns 0 and expired message`() {
        val (code, out, err) = run(
            arrayOf(fixturesPath.toString()),
            now = LocalDateTime.parse("2027-01-01T08:00"),
        )
        assertEquals(0, code)
        assertTrue(err.isEmpty(), "stderr should be empty")
        assertTrue(out.contains("2026-12-31"), "output should mention last service date")
        assertTrue(out.contains("expired"), "output should contain 'expired'")
    }
}
