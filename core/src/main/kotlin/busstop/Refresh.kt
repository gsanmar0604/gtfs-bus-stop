package busstop

import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Minutes from [now] until [departure], as a reader of the screen would count them:
 * the difference between the two HH:mm times shown. Never negative.
 *
 * Both times are cut to whole minutes first. Otherwise the seconds of "now"
 * (the refresh wakes a few ms after the minute; the first screen after launch can be
 * at any second) would make the count one less than the times on screen suggest.
 */
fun minutesUntil(departure: LocalDateTime, now: LocalDateTime): Long =
    maxOf(
        0,
        Duration.between(
            now.truncatedTo(ChronoUnit.MINUTES),
            departure.truncatedTo(ChronoUnit.MINUTES),
        ).toMinutes(),
    )

/**
 * How long to wait from [now] until the start of the next minute.
 *
 * Always returns a positive duration: at an exact minute boundary (e.g. 08:00:00)
 * we wait a full 60 s for 08:01:00, because the display (HH:mm) won't change until then.
 */
fun delayUntilNextMinute(now: LocalDateTime): Duration {
    val elapsed = Duration.ofSeconds(now.second.toLong()).plusNanos(now.nano.toLong())
    return Duration.ofMinutes(1).minus(elapsed)
}
