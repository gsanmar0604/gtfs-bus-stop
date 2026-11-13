package busstop

import java.time.Duration
import java.time.LocalDateTime

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
