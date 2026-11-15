package busstop

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

private const val FEED_FILENAME = "bizkaibus.zip"

class BusStopViewModel @JvmOverloads constructor(
    app: Application,
    private val clock: Clock = Clock.system(ZoneId.of(MADRID_ZONE)),
) : AndroidViewModel(app) {

    // Cached after the first load so a screen rotation doesn't re-parse the zip.
    private var feed: GtfsFeed? = null

    // The flow runs only while the screen is subscribed (WhileSubscribed).
    // Coming back to the app after > 5 s restarts the flow, which recomputes
    // immediately — the screen is never stale after the phone wakes.
    // .catch replaces runCatching so coroutine cancellation is not accidentally swallowed.
    val state: StateFlow<FeedState> = flow<FeedState> {
        val f = feed ?: withContext(Dispatchers.IO) { loadFeed() }.also { feed = it }
        val stopName = f.stopName(TARGET_STOP) ?: error("Stop $TARGET_STOP not found in feed")
        // Recompute at every minute boundary; delayUntilNextMinute uses a fresh `now`
        // each iteration so the loop cannot drift.
        while (true) {
            val now = LocalDateTime.now(clock)
            val departures = f.nextDepartures(TARGET_STOP, now)
            val expiredOn = if (departures.isEmpty())
                f.lastServiceDate?.takeIf { now.toLocalDate().isAfter(it) }
            else null
            emit(FeedState.Ready(stopName, departures, now, expiredOn))
            delay(delayUntilNextMinute(now).toMillis())
        }
    }
        .catch { emit(FeedState.Error(it.message ?: "Unknown error")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedState.Loading)

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
        } catch (_: FileNotFoundException) {
            throw IllegalStateException(
                "GTFS feed not bundled.\n\nPut bizkaibus.zip in data/ and rebuild the app."
            )
        }
        return GtfsFeed.load(zipFile.toPath(), stopIds = setOf(TARGET_STOP))
    }
}
