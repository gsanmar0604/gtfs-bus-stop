package busstop

import java.nio.file.Paths
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err.println("Usage: subset-feed <source-zip-or-dir> <target-zip>")
        exitProcess(1)
    }
    val source = Paths.get(args[0])
    val target = Paths.get(args[1])
    if (!source.toFile().exists()) {
        System.err.println("Error: source not found: ${args[0]}")
        exitProcess(1)
    }
    writeStopSubset(source, TARGET_STOP, target)
    println("Written: $target")
}
