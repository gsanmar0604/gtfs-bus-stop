package busstop

import java.time.LocalDateTime

sealed class FeedState {
    object Loading : FeedState()
    data class Ready(
        val stopName: String,
        val departures: List<Departure>,
        val updatedAt: LocalDateTime,
    ) : FeedState()
    data class Error(val message: String) : FeedState()
}
