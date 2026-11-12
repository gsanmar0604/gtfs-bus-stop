package busstop

fun main(args: Array<String>) {
    // Entry point for the "Basic GTFS Parsing" feature:
    // parse a static GTFS dataset and print the next 3 departures
    // for the Target Stop in Markdown.
    println("Usage: gtfs-bus-stop <path-to-gtfs-zip-or-directory>")
    if (args.isNotEmpty()) {
        println("GTFS source: ${args[0]}")
    }
}
