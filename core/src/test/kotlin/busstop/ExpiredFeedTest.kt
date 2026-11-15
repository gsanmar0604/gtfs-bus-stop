package busstop

import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExpiredFeedTest {

    private val feed by lazy {
        GtfsFeed.load(Path.of(javaClass.getResource("/fixtures")!!.toURI()))
    }

    @Test
    fun `lastServiceDate of the fixtures is 2026-12-31`() {
        assertEquals(LocalDate.parse("2026-12-31"), feed.lastServiceDate)
    }

    @Test
    fun `expiredOn returns null on the last service date`() {
        assertEquals(null, feed.expiredOn(LocalDateTime.parse("2026-12-31T12:00")))
    }

    @Test
    fun `expiredOn returns the last service date the day after`() {
        assertEquals(LocalDate.parse("2026-12-31"), feed.expiredOn(LocalDateTime.parse("2027-01-01T08:00")))
    }

    @Test
    fun `expiredOn is non-null at 00-30 on 2027-01-01 but T7 25h10 departure is still returned`() {
        // The 2026-12-31 service (Thursday) has a 25:10 trip → real time 2027-01-01 01:10.
        // The feed is technically "expired" at 00:30 that same day, but the departure
        // is still future; nextDepartures must return it, and the renderer decides what to show.
        val now = LocalDateTime.parse("2027-01-01T00:30")
        assertNotNull(feed.expiredOn(now))
        val departures = feed.nextDepartures("S1", now)
        assertTrue(departures.isNotEmpty(), "T7 at 01:10 should still be returned")
        assertEquals(LocalDateTime.parse("2027-01-01T01:10"), departures[0].time)
    }
}
