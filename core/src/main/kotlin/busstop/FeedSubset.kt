package busstop

import java.io.OutputStreamWriter
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a minimal GTFS zip to [target] containing only what stop [stopId] needs:
 *   - stop_times.txt: the [stopId] rows + each serving trip's highest-stop_sequence row.
 *     The last-stop row is kept so [GtfsFeed.load] can still apply the boarding rule
 *     ("last stop of a trip" is not boardable). Without it every trip would look like
 *     it ends at the target stop and all departures would be filtered out.
 *   - trips.txt: only trips that serve [stopId].
 *   - stops.txt, routes.txt, calendar.txt, calendar_dates.txt, agency.txt: copied unchanged.
 */
fun writeStopSubset(source: Path, stopId: String, target: Path) =
    withGtfsFiles(source) { open -> buildSubset(open, stopId, target) }

private fun buildSubset(open: (String) -> Reader?, stopId: String, target: Path) {
    // Pass 1: find all trips serving the stop and their max stop_sequence across the whole file.
    val servingTrips = mutableSetOf<String>()
    val maxSeqByTrip = mutableMapOf<String, Int>()
    open("stop_times.txt")?.use { r ->
        for (row in parseCsv(r)) {
            val tripId = row.required("trip_id", "stop_times.txt")
            val seq = row.required("stop_sequence", "stop_times.txt").trim().toInt()
            if (seq > (maxSeqByTrip[tripId] ?: 0)) maxSeqByTrip[tripId] = seq
            if (row.required("stop_id", "stop_times.txt") == stopId) servingTrips.add(tripId)
        }
    }

    Files.createDirectories(target.parent)
    ZipOutputStream(Files.newOutputStream(target)).use { zout ->
        writeFiltered(open, "stop_times.txt", zout) { row ->
            val tripId = row.required("trip_id", "stop_times.txt")
            if (tripId !in servingTrips) return@writeFiltered false
            val seq = row.required("stop_sequence", "stop_times.txt").trim().toInt()
            row.required("stop_id", "stop_times.txt") == stopId || seq == maxSeqByTrip[tripId]
        }
        writeFiltered(open, "trips.txt", zout) { row ->
            row.required("trip_id", "trips.txt") in servingTrips
        }
        for (name in listOf("stops.txt", "routes.txt", "calendar.txt", "calendar_dates.txt", "agency.txt")) {
            copyEntry(open, name, zout)
        }
    }
}

/**
 * Copies lines from [fileName] to a new ZIP entry, keeping only lines where [keep] returns true.
 * Lines are written verbatim (not re-serialised) so original quoting is preserved.
 */
private fun writeFiltered(
    open: (String) -> Reader?,
    fileName: String,
    out: ZipOutputStream,
    keep: (Map<String, String>) -> Boolean,
) {
    val reader = open(fileName) ?: return
    out.putNextEntry(ZipEntry(fileName))
    val writer = out.bufferedWriter(Charsets.UTF_8)
    reader.use {
        val br = it.buffered()
        // Skip UTF-8 BOM (U+FEFF) if present so the header line is clean.
        br.mark(1)
        if (br.read() != 0xFEFF) br.reset()
        val headerLine = br.readLine() ?: return@use
        val headers = splitCsvLine(headerLine).map { h -> h.trim() }
        writer.write(headerLine); writer.newLine()
        while (true) {
            val line = br.readLine() ?: break
            if (line.isBlank()) continue
            val row = headers.zip(splitCsvLine(line)).toMap()
            if (keep(row)) { writer.write(line); writer.newLine() }
        }
    }
    writer.flush()
    out.closeEntry()
}

/** Copies [fileName] unchanged as a character stream. */
private fun copyEntry(open: (String) -> Reader?, fileName: String, out: ZipOutputStream) {
    val reader = open(fileName) ?: return
    out.putNextEntry(ZipEntry(fileName))
    reader.use { r ->
        val w = OutputStreamWriter(out, Charsets.UTF_8)
        r.copyTo(w)
        w.flush()
    }
    out.closeEntry()
}
