package busstop

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

private const val TARGET_STOP = "4255"
private const val FEED_FILENAME = "bizkaibus.zip"

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
        val app = getApplication<Application>()
        val now = LocalDateTime.now(ZoneId.of("Europe/Madrid"))

        // Look for the real Bizkaibus zip in the app's external files directory (no extra
        // permissions needed — it is the app's own storage).
        // Push it before a demo with:
        //   adb push data/bizkaibus.zip /sdcard/Android/data/busstop.app/files/
        val externalZip = app.getExternalFilesDir(null)?.let { File(it, FEED_FILENAME) }
        if (externalZip != null && externalZip.exists()) {
            val feed = GtfsFeed.load(externalZip.toPath(), stopIds = setOf(TARGET_STOP))
            val stopName = feed.stopName(TARGET_STOP)
                ?: return FeedState.Error("Stop $TARGET_STOP not found in feed")
            return FeedState.Ready(stopName, feed.nextDepartures(TARGET_STOP, now))
        }

        // Real zip not on device — fall back to the bundled hand-written fixtures.
        return loadFixtureData(app, now)
    }

    private fun loadFixtureData(app: Application, now: LocalDateTime): FeedState {
        // The bundled assets are not on the filesystem, so we extract them to the
        // app's cache directory before calling GtfsFeed.load().
        val cacheDir = app.cacheDir.resolve("gtfs")
        cacheDir.mkdirs()
        listOf("agency.txt", "calendar.txt", "calendar_dates.txt",
               "routes.txt", "stops.txt", "stop_times.txt", "trips.txt")
            .forEach { name ->
                app.assets.open("gtfs/$name").use { it.copyTo(cacheDir.resolve(name).outputStream()) }
            }
        val feed = GtfsFeed.load(cacheDir.toPath())
        val stopName = feed.stopName("S1") ?: return FeedState.Error("Fixture data missing")
        return FeedState.Ready(stopName, feed.nextDepartures("S1", now))
    }
}
