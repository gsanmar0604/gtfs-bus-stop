package busstop

import java.io.PrintStream
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.system.exitProcess

/**
 * The whole CLI, testable: returns the exit code instead of calling exitProcess.
 * [stopId] and [now] are injectable so tests can use the fixture feed at a fixed time.
 */
fun runCli(
    args: Array<String>,
    now: LocalDateTime,
    out: PrintStream,
    err: PrintStream,
    stopId: String = TARGET_STOP,
): Int {
    if (args.isEmpty()) {
        err.println("Usage: gtfs-bus-stop <path-to-gtfs-zip-or-directory>")
        return 1
    }

    val source = Paths.get(args[0])
    if (!source.toFile().exists()) {
        err.println("Error: path not found: ${args[0]}")
        return 1
    }

    val feed = try {
        GtfsFeed.load(source, stopIds = setOf(stopId))
    } catch (e: GtfsFormatException) {
        err.println("Error: ${e.message}")
        return 1
    }

    val stopName = feed.stopName(stopId)
    if (stopName == null) {
        err.println("Error: stop $stopId not found in feed")
        return 1
    }

    val departures = feed.nextDepartures(stopId, now)
    out.print(renderMarkdown(stopName, departures, feed.expiredOn(now)))
    return 0
}

fun main(args: Array<String>) {
    exitProcess(runCli(args, LocalDateTime.now(ZoneId.of(MADRID_ZONE)), System.out, System.err))
}
