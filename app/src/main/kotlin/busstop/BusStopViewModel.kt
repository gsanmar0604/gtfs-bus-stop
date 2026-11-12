package busstop

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

private const val TARGET_STOP = "S1"

class BusStopViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<FeedState>(FeedState.Loading)
    val state: StateFlow<FeedState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = runCatching { loadDepartures() }
                .fold(onSuccess = { it }, onFailure = { FeedState.Error(it.message ?: "Unknown error") })
        }
    }

    private fun loadDepartures(): FeedState {
        // The bundled GTFS assets are not on the filesystem, so we extract them to
        // the app's cache directory before calling GtfsFeed.load().
        val cacheDir = getApplication<Application>().cacheDir.resolve("gtfs")
        cacheDir.mkdirs()
        val assets = getApplication<Application>().assets
        listOf("agency.txt", "calendar.txt", "calendar_dates.txt",
               "routes.txt", "stops.txt", "stop_times.txt", "trips.txt")
            .forEach { file ->
                assets.open("gtfs/$file").use { input ->
                    cacheDir.resolve(file).outputStream().use { input.copyTo(it) }
                }
            }

        val feed = GtfsFeed.load(cacheDir.toPath())
        val stopName = feed.stopName(TARGET_STOP)
            ?: return FeedState.Error("Stop $TARGET_STOP not found in feed")
        val now = LocalDateTime.now(ZoneId.of("Europe/Madrid"))
        return FeedState.Ready(stopName, feed.nextDepartures(TARGET_STOP, now))
    }
}
