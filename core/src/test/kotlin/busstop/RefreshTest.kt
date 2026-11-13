package busstop

import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class RefreshTest {

    // ── minutesUntil ─────────────────────────────────────────────────────────

    @Test
    fun `minutesUntil truncates to full minutes`() {
        val now = LocalDateTime.parse("2026-11-13T14:00:00")
        assertEquals(4L, minutesUntil(now.plusMinutes(4).plusSeconds(30), now))
    }

    @Test
    fun `minutesUntil returns 0 when departure is exactly now`() {
        val now = LocalDateTime.parse("2026-11-13T14:00:00")
        assertEquals(0L, minutesUntil(now, now))
    }

    @Test
    fun `minutesUntil matches the HH-mm times on screen when the tick fires a bit late`() {
        // The refresh loop wakes a few milliseconds after the minute starts.
        val lateTick = LocalDateTime.parse("2026-11-13T08:00:00.050")
        assertEquals(5L, minutesUntil(LocalDateTime.parse("2026-11-13T08:05"), lateTick))
        assertEquals(1L, minutesUntil(LocalDateTime.parse("2026-11-13T08:01"), lateTick))
    }

    @Test
    fun `minutesUntil matches the HH-mm times on screen when loading finishes mid-minute`() {
        // First screen after launch: 17:58 shown, bus at 18:00 (real feed times have seconds).
        val midMinute = LocalDateTime.parse("2026-11-13T17:58:30")
        assertEquals(2L, minutesUntil(LocalDateTime.parse("2026-11-13T18:00:02"), midMinute))
    }

    // ── delayUntilNextMinute ──────────────────────────────────────────────────

    @Test
    fun `at an exact minute boundary the delay is a full 60 seconds`() {
        // The UI shows HH:mm; at 08:00:00 nothing changes until 08:01:00, so we wait the full minute.
        assertEquals(
            Duration.ofSeconds(60),
            delayUntilNextMinute(LocalDateTime.parse("2026-11-12T08:00:00")),
        )
    }

    @Test
    fun `half a second before the next minute the delay is 500 ms`() {
        assertEquals(
            Duration.ofMillis(500),
            delayUntilNextMinute(LocalDateTime.parse("2026-11-12T08:00:59.500")),
        )
    }

    @Test
    fun `crossing midnight, 30 seconds before 00h00 gives 30 seconds`() {
        assertEquals(
            Duration.ofSeconds(30),
            delayUntilNextMinute(LocalDateTime.parse("2026-11-12T23:59:30")),
        )
    }

    // ── Moving-time-forward story ─────────────────────────────────────────────

    private val feed: GtfsFeed by lazy {
        GtfsFeed.load(Path.of(javaClass.getResource("/fixtures")!!.toURI()))
    }

    private fun summary(d: List<Departure>) = d.map { "${it.line} ${it.time}" }

    @Test
    fun `minute by minute, a past departure drops off and the next one appears`() {
        // 07:59 — 08:00 bus is still upcoming.
        assertEquals(
            listOf("L1 2026-11-12T08:00", "L1 2026-11-12T08:15", "L2 2026-11-12T08:30"),
            summary(feed.nextDepartures("S1", LocalDateTime.parse("2026-11-12T07:59"))),
        )
        // 08:01 — 08:00 bus has gone; 09:00 becomes the third departure.
        assertEquals(
            listOf("L1 2026-11-12T08:15", "L2 2026-11-12T08:30", "L1 2026-11-12T09:00"),
            summary(feed.nextDepartures("S1", LocalDateTime.parse("2026-11-12T08:01"))),
        )
        // 23:59 — the 25:10 GTFS trip (01:10 Friday) is now first.
        assertEquals(
            listOf("L2 2026-11-13T01:10", "L1 2026-11-13T08:00", "L1 2026-11-13T08:15"),
            summary(feed.nextDepartures("S1", LocalDateTime.parse("2026-11-12T23:59"))),
        )
    }
}
