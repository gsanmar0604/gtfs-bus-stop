package busstop

import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("Usage: gtfs-bus-stop <path-to-gtfs-zip-or-directory>")
        exitProcess(1)
    }

    val source = Paths.get(args[0])
    if (!source.toFile().exists()) {
        System.err.println("Error: path not found: ${args[0]}")
        exitProcess(1)
    }

    val feed = try {
        GtfsFeed.load(source, stopIds = setOf(TARGET_STOP))
    } catch (e: GtfsFormatException) {
        System.err.println("Error: ${e.message}")
        exitProcess(1)
    }

    val stopName = feed.stopName(TARGET_STOP)
    if (stopName == null) {
        System.err.println("Error: stop $TARGET_STOP not found in feed")
        exitProcess(1)
    }

    val now = LocalDateTime.now(ZoneId.of(MADRID_ZONE))
    val departures = feed.nextDepartures(TARGET_STOP, now)
    print(renderMarkdown(stopName, departures))
}
