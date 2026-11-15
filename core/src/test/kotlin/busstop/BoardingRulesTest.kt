package busstop

import java.time.LocalDateTime
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the boarding-rules filter applied at feed load time:
 *   - pickup_type = 1 (no pickup at this stop) → skipped
 *   - last stop of its trip → skipped even without pickup_type = 1
 *   - normal stop the trip continues past → kept
 */
class BoardingRulesTest {

    @Test
    fun `only boardable stop times appear in next departures`() {
        val dir = createTempDirectory()
        try {
            writeBoardingRulesFeed(dir)

            val now = LocalDateTime.parse("2026-11-13T07:00")
            val departures = GtfsFeed.load(dir, stopIds = setOf("S1")).nextDepartures("S1", now)

            // Only T_NORMAL (08:00) and T_CONTINUES (09:40) are boardable at S1.
            assertEquals(2, departures.size)
            assertEquals(LocalDateTime.parse("2026-11-13T08:00"), departures[0].time)
            assertEquals(LocalDateTime.parse("2026-11-13T09:40"), departures[1].time)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
