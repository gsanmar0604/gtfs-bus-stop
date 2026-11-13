package busstop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusStopScreen(viewModel: BusStopViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val title = (state as? FeedState.Ready)?.stopName ?: "Bus Stop"

    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }) }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (val s = state) {
                is FeedState.Loading -> Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading timetable…")
                }
                is FeedState.Error -> Text("Error: ${s.message}", modifier = Modifier.padding(16.dp))
                is FeedState.Ready -> DeparturePanel(s.departures, s.updatedAt)
            }
        }
    }
}

@Composable
private fun DeparturePanel(departures: List<Departure>, updatedAt: LocalDateTime) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            "Updated ${updatedAt.format(TIME_FMT)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            if (departures.isEmpty()) {
                Text("No upcoming departures.", modifier = Modifier.padding(16.dp))
            } else {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    DepartureTableHeader()
                    HorizontalDivider()
                    departures.forEach { DepartureRow(it, updatedAt) }
                }
            }
        }
    }
}

@Composable
private fun DepartureTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Time",        modifier = Modifier.width(64.dp), style = MaterialTheme.typography.labelLarge)
        Text("Wait",        modifier = Modifier.width(88.dp), style = MaterialTheme.typography.labelLarge)
        Text("Line",        modifier = Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge)
        Text("Destination",                                    style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun DepartureRow(departure: Departure, now: LocalDateTime) {
    val minutes = minutesUntil(departure.time, now)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            departure.time.format(TIME_FMT),
            modifier = Modifier.width(64.dp),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            if (minutes == 0L) "now" else "in $minutes min",
            modifier = Modifier.width(88.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Box(modifier = Modifier.width(72.dp)) {
            Text(
                departure.line,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Text(departure.headsign, style = MaterialTheme.typography.bodyMedium)
    }
}
