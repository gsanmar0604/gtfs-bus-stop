package busstop

import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Uses the hand-written feed in src/test/resources/fixtures.
 * See fixtures/README.md for the schedule these tests rely on.
 */
class NextDeparturesTest {

    private val feed: GtfsFeed by lazy {
        val dir = Path.of(javaClass.getResource("/fixtures")!!.toURI())
        GtfsFeed.load(dir)
    }

    private fun at(dateTime: String): LocalDateTime = LocalDateTime.parse(dateTime)

    /** "L1 2026-11-12T08:00" style summaries keep assertions short and readable. */
    private fun summary(departures: List<Departure>): List<String> =
        departures.map { "${it.line} ${it.time}" }

    // Thursday 2026-11-12: WEEKDAY service runs.

    @Test
    fun `returns the next 3 departures in time order`() {
        val result = feed.nextDepartures("S1", at("2026-11-12T07:00"))

        assertEquals(
            listOf("L1 2026-11-12T08:00", "L1 2026-11-12T08:15", "L2 2026-11-12T08:30"),
            summary(result),
        )
    }

    @Test
    fun `skips departures that already left`() {
        val result = feed.nextDepartures("S1", at("2026-11-12T08:20"))

        assertEquals(
            listOf("L2 2026-11-12T08:30", "L1 2026-11-12T09:00", "L1 2026-11-12T09:30"),
            summary(result),
        )
    }

    @Test
    fun `a departure exactly at the current time is still included`() {
        val result = feed.nextDepartures("S1", at("2026-11-12T08:00"))

        assertEquals("L1 2026-11-12T08:00", summary(result).first())
    }

    @Test
    fun `respects a custom limit`() {
        val result = feed.nextDepartures("S1", at("2026-11-12T07:00"), limit = 1)

        assertEquals(listOf("L1 2026-11-12T08:00"), summary(result))
    }

    @Test
    fun `includes the headsign of each trip`() {
        val result = feed.nextDepartures("S1", at("2026-11-12T08:20"))

        assertEquals(listOf("Basurto", "Centro", "Centro"), result.map { it.headsign })
    }

    @Test
    fun `continues into the next day when today's departures run out`() {
        val result = feed.nextDepartures("S1", at("2026-11-12T09:10"))

        // T5 at 09:30, T7 at 25:10 (= 01:10 the next day), then Friday's first bus.
        assertEquals(
            listOf("L1 2026-11-12T09:30", "L2 2026-11-13T01:10", "L1 2026-11-13T08:00"),
            summary(result),
        )
    }

    // Times past 24:00:00.

    @Test
    fun `after midnight, includes trips of the previous service day`() {
        // Friday 00:30. T7 belongs to Thursday's service but leaves at 01:10 Friday.
        val result = feed.nextDepartures("S1", at("2026-11-13T00:30"))

        assertEquals(
            listOf("L2 2026-11-13T01:10", "L1 2026-11-13T08:00", "L1 2026-11-13T08:15"),
            summary(result),
        )
    }

    // Service calendar.

    @Test
    fun `weekend service replaces weekday service on Saturday and Sunday`() {
        // Saturday 2026-11-14: only T6 runs (10:00), again on Sunday,
        // and the weekday trips come back on Monday.
        val result = feed.nextDepartures("S1", at("2026-11-14T09:00"))

        assertEquals(
            listOf("L1 2026-11-14T10:00", "L1 2026-11-15T10:00", "L1 2026-11-16T08:00"),
            summary(result),
        )
    }

    @Test
    fun `calendar_dates can remove and add services on a holiday`() {
        // Tuesday 2026-12-08: WEEKDAY removed, WEEKEND added (T6 at 10:00).
        // Wednesday is a normal weekday again.
        val result = feed.nextDepartures("S1", at("2026-12-08T07:00"))

        assertEquals(
            listOf("L1 2026-12-08T10:00", "L1 2026-12-09T08:00", "L1 2026-12-09T08:15"),
            summary(result),
        )
    }

    @Test
    fun `returns nothing after the last day of the feed`() {
        val result = feed.nextDepartures("S1", at("2027-01-01T07:00"))

        assertEquals(emptyList(), result)
    }

    // Stops.

    @Test
    fun `only returns departures of the requested stop`() {
        // T8 only serves S2, so it must not appear for S1 and must appear for S2.
        val s1 = feed.nextDepartures("S1", at("2026-11-12T07:00"), limit = 10)
        val s2 = feed.nextDepartures("S2", at("2026-11-12T07:00"))

        assertEquals(false, s1.any { it.time == at("2026-11-12T08:20") })
        assertEquals(
            listOf("L1 2026-11-12T07:50", "L1 2026-11-12T08:05", "L2 2026-11-12T08:20"),
            summary(s2),
        )
    }

    @Test
    fun `unknown stop has no departures and no name`() {
        assertEquals(emptyList(), feed.nextDepartures("NOPE", at("2026-11-12T07:00")))
        assertNull(feed.stopName("NOPE"))
    }

    @Test
    fun `finds the stop name`() {
        assertEquals("Plaza Eliptica", feed.stopName("S1"))
    }
}
