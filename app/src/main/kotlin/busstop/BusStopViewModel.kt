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
        // The feed lives in the app's external files directory (no extra permissions needed).
        // Push it before opening the app:
        //   adb push data/bizkaibus.zip /sdcard/Android/data/busstop.app/files/
        val externalDir = getApplication<Application>().getExternalFilesDir(null)
        val externalZip = externalDir?.let { File(it, FEED_FILENAME) }

        if (externalZip == null || !externalZip.exists()) {
            val targetPath = externalDir?.absolutePath ?: "the app's external files directory"
            return FeedState.Error(
                "No GTFS feed found.\n\nPush it with:\nadb push data/bizkaibus.zip $targetPath/"
            )
        }

        val feed = GtfsFeed.load(externalZip.toPath(), stopIds = setOf(TARGET_STOP))
        val stopName = feed.stopName(TARGET_STOP)
            ?: return FeedState.Error("Stop $TARGET_STOP not found in feed")
        val now = LocalDateTime.now(ZoneId.of(MADRID_ZONE))
        return FeedState.Ready(stopName, feed.nextDepartures(TARGET_STOP, now))
    }
}
