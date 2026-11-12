package busstop

import java.nio.file.Path
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals

class GtfsZipTest {

    @Test
    fun `loading from a zip gives the same results as loading from the directory`() {
        val dir = Path.of(javaClass.getResource("/fixtures")!!.toURI())
        val now = LocalDateTime.parse("2026-11-12T07:00")

        // Build a zip from the fixture files in a temp file.
        val zip = createTempFile(suffix = ".zip")
        try {
            ZipOutputStream(zip.toFile().outputStream()).use { zos ->
                dir.toFile().listFiles()!!.filter { it.isFile }.forEach { file ->
                    zos.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }

            val fromDir = GtfsFeed.load(dir)
            val fromZip = GtfsFeed.load(zip)

            assertEquals(fromDir.stopName("S1"), fromZip.stopName("S1"))
            assertEquals(
                fromDir.nextDepartures("S1", now),
                fromZip.nextDepartures("S1", now),
            )
        } finally {
            zip.toFile().delete()
        }
    }
}
