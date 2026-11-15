package busstop

import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ExpiredFeedTest {

    private val feed by lazy {
        GtfsFeed.load(Path.of(javaClass.getResource("/fixtures")!!.toURI()))
    }

    @Test
    fun `lastServiceDate of the fixtures is 2026-12-31`() {
        assertEquals(LocalDate.parse("2026-12-31"), feed.lastServiceDate)
    }
}
