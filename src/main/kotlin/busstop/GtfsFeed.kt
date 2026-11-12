package busstop

import java.nio.file.Path
import java.time.LocalDateTime

/** A parsed static GTFS dataset. */
class GtfsFeed private constructor() {

    /** Name of the stop, or null if the stop does not exist. */
    fun stopName(stopId: String): String? = TODO("not implemented yet")

    /**
     * The next [limit] departures from [stopId] at or after [now],
     * ordered by time.
     */
    fun nextDepartures(stopId: String, now: LocalDateTime, limit: Int = 3): List<Departure> =
        TODO("not implemented yet")

    companion object {
        /** Loads a feed from a directory containing the GTFS .txt files. */
        fun load(directory: Path): GtfsFeed = TODO("not implemented yet")
    }
}
