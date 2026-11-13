package busstop

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.time.LocalDateTime
import java.time.ZoneId

private const val FEED_FILENAME = "bizkaibus.zip"

class BusStopViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<FeedState>(FeedState.Loading)
    val state: StateFlow<FeedState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Load the feed once on IO; parsing the real zip takes a few seconds.
            val feed = withContext(Dispatchers.IO) { runCatching { loadFeed() } }
                .onFailure { _state.value = FeedState.Error(it.message ?: "Unknown error") }
                .getOrNull() ?: return@launch

            val stopName = feed.stopName(TARGET_STOP) ?: run {
                _state.value = FeedState.Error("Stop $TARGET_STOP not found in feed")
                return@launch
            }

            // Recompute and republish at every minute boundary.
            // delayUntilNextMinute uses a fresh `now` each iteration, so the loop
            // cannot drift: a late wake-up just shortens the next wait.
            while (true) {
                val now = LocalDateTime.now(ZoneId.of(MADRID_ZONE))
                _state.value = FeedState.Ready(stopName, feed.nextDepartures(TARGET_STOP, now), now)
                delay(delayUntilNextMinute(now).toMillis())
            }
        }
    }

    private fun loadFeed(): GtfsFeed {
        // The zip is packed into the APK at build time (see app/build.gradle.kts).
        // Assets can only be read as streams, but ZipFile needs a real file,
        // so copy it to the app's cache directory first.
        val app = getApplication<Application>()
        val zipFile = File(app.cacheDir, FEED_FILENAME)
        try {
            app.assets.open(FEED_FILENAME).use { input ->
                zipFile.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: FileNotFoundException) {
            throw IllegalStateException(
                "GTFS feed not bundled.\n\nPut bizkaibus.zip in data/ and rebuild the app."
            )
        }
        return GtfsFeed.load(zipFile.toPath(), stopIds = setOf(TARGET_STOP))
    }
}
