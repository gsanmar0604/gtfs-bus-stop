package busstop

import java.time.LocalDateTime

/**
 * One scheduled departure from a stop.
 *
 * [time] is a real date and time: a GTFS time such as 25:10:00 on service day
 * D is represented as 01:10 on day D+1.
 */
data class Departure(
    val line: String,
    val headsign: String,
    val time: LocalDateTime,
)
