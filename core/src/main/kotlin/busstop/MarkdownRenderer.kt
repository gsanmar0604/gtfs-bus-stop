package busstop

import java.time.format.DateTimeFormatter

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

fun renderMarkdown(stopName: String, departures: List<Departure>): String = buildString {
    appendLine("## Next departures at $stopName")
    appendLine()
    if (departures.isEmpty()) {
        appendLine("No upcoming departures.")
    } else {
        appendLine("| Time | Line | Destination |")
        appendLine("|------|------|-------------|")
        for (d in departures) {
            appendLine("| ${d.time.format(TIME_FMT)} | ${d.line} | ${d.headsign} |")
        }
    }
}
