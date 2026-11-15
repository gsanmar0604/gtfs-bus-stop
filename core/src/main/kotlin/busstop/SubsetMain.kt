package busstop

import java.io.PrintStream
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * The whole subset-feed CLI, testable: returns the exit code instead of calling exitProcess.
 * A [GtfsFormatException] is caught and printed so the Gradle task fails with a clear message
 * rather than a raw stack trace.
 */
fun runSubset(
    args: Array<String>,
    err: PrintStream,
    stopId: String = TARGET_STOP,
): Int {
    if (args.size != 2) {
        err.println("Usage: subset-feed <source-zip-or-dir> <target-zip>")
        return 1
    }
    val source = Paths.get(args[0])
    val target = Paths.get(args[1])
    if (!source.toFile().exists()) {
        err.println("Error: source not found: ${args[0]}")
        return 1
    }
    return try {
        writeStopSubset(source, stopId, target)
        println("Written: $target")
        0
    } catch (e: GtfsFormatException) {
        err.println("Error: ${e.message}")
        1
    }
}

fun main(args: Array<String>) {
    exitProcess(runSubset(args, System.err))
}
