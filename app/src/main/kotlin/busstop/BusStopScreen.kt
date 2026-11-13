package busstop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusStopScreen(viewModel: BusStopViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("GTFS Bus Stop") }) }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (val s = state) {
                is FeedState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(32.dp))
                is FeedState.Error   -> Text("Error: ${s.message}", modifier = Modifier.padding(16.dp))
                is FeedState.Ready   -> DeparturePanel(s.stopName, s.departures, s.updatedAt)
            }
        }
    }
}

@Composable
private fun DeparturePanel(stopName: String, departures: List<Departure>, updatedAt: LocalDateTime) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(stopName, style = MaterialTheme.typography.headlineMedium)
        Text(
            "Updated ${updatedAt.format(TIME_FMT)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (departures.isEmpty()) {
            Text("No upcoming departures.")
        } else {
            DepartureTableHeader()
            HorizontalDivider()
            departures.forEach { DepartureRow(it) }
        }
    }
}

@Composable
private fun DepartureTableHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("Time",        modifier = Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge)
        Text("Line",        modifier = Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge)
        Text("Destination",                                   style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun DepartureRow(departure: Departure) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(departure.time.format(TIME_FMT), modifier = Modifier.width(72.dp))
        Text(departure.line,                  modifier = Modifier.width(72.dp))
        Text(departure.headsign)
    }
}
