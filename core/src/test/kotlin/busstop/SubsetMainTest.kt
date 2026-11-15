package busstop

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubsetMainTest {

    private val fixturesPath get() = Path.of(javaClass.getResource("/fixtures")!!.toURI())

    private fun run(args: Array<String>, stopId: String = "S1"): Triple<Int, String, String> {
        val outBuf = ByteArrayOutputStream()
        val errBuf = ByteArrayOutputStream()
        val realOut = System.out
        System.setOut(PrintStream(outBuf))
        val exitCode = try {
            runSubset(args, PrintStream(errBuf), stopId)
        } finally {
            System.setOut(realOut)
        }
        return Triple(exitCode, outBuf.toString(Charsets.UTF_8), errBuf.toString(Charsets.UTF_8))
    }

    @Test
    fun `wrong argument count prints usage and returns 1`() {
        val (code, _, err) = run(emptyArray())
        assertEquals(1, code)
        assertTrue(err.contains("Usage"), "err should contain 'Usage'")
    }

    @Test
    fun `missing source returns 1 with error message`() {
        val tmp = Files.createTempFile("out", ".zip")
        try {
            val (code, _, err) = run(arrayOf("/no/such/source", tmp.toString()))
            assertEquals(1, code)
            assertTrue(err.contains("not found"), "err should mention 'not found'")
        } finally { Files.deleteIfExists(tmp) }
    }

    @Test
    fun `malformed feed returns 1 with GtfsFormatException message`() {
        val dir = createTempDirectory()
        val tmp = Files.createTempFile("out", ".zip")
        try {
            dir.resolve("stops.txt").writeText("stop_id,stop_name\nS1,Test\nS2,Other\n")
            dir.resolve("routes.txt").writeText("route_id,route_short_name\nR1,L1\n")
            dir.resolve("trips.txt").writeText("trip_id,route_id,service_id\nT1,R1,SVC\n")
            // stop_sequence column missing → GtfsFormatException
            dir.resolve("stop_times.txt").writeText("trip_id,departure_time,stop_id\nT1,08:00:00,S1\n")
            dir.resolve("calendar_dates.txt").writeText("service_id,date,exception_type\nSVC,20261113,1\n")
            val (code, _, err) = run(arrayOf(dir.toString(), tmp.toString()))
            assertEquals(1, code)
            assertTrue(err.contains("stop_times.txt"), "err should name the file")
        } finally {
            dir.toFile().deleteRecursively()
            Files.deleteIfExists(tmp)
        }
    }

    @Test
    fun `success with fixtures writes target zip and returns 0`() {
        val tmp = Files.createTempFile("subset", ".zip")
        try {
            val (code, _, err) = run(arrayOf(fixturesPath.toString(), tmp.toString()))
            assertEquals(0, code)
            assertTrue(err.isEmpty(), "stderr should be empty")
            assertTrue(Files.size(tmp) > 0, "target zip should not be empty")
        } finally { Files.deleteIfExists(tmp) }
    }
}
