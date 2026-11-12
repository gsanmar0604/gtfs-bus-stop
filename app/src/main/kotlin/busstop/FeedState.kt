package busstop

sealed class FeedState {
    object Loading : FeedState()
    data class Ready(val stopName: String, val departures: List<Departure>) : FeedState()
    data class Error(val message: String) : FeedState()
}
