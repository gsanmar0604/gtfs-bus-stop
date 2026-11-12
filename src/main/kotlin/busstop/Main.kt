package busstop

import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.system.exitProcess

private const val TARGET_STOP = "4255"
private val MADRID = ZoneId.of("Europe/Madrid")

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("Usage: gtfs-bus-stop <path-to-gtfs-zip-or-directory>")
        exitProcess(1)
    }

    val source = Path.of(args[0])
    if (!source.toFile().exists()) {
        System.err.println("Error: path not found: ${args[0]}")
        exitProcess(1)
    }

    val feed = GtfsFeed.load(source, stopIds = setOf(TARGET_STOP))

    val stopName = feed.stopName(TARGET_STOP)
    if (stopName == null) {
        System.err.println("Error: stop $TARGET_STOP not found in feed")
        exitProcess(1)
    }

    val now = LocalDateTime.now(MADRID)
    val departures = feed.nextDepartures(TARGET_STOP, now)
    print(renderMarkdown(stopName, departures))
}
