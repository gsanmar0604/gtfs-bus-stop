package busstop

import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * Opens the GTFS files of [source] (a .zip or a directory), calls [block] with an [open]
 * function that returns a [Reader] for a named file (null if the file doesn't exist),
 * then closes the zip if one was opened.
 */
internal fun <T> withGtfsFiles(source: Path, block: (opener: (String) -> Reader?) -> T): T =
    if (source.toString().endsWith(".zip")) {
        ZipFile(source.toFile()).use { zip ->
            block { name -> zip.getEntry(name)?.let { zip.getInputStream(it).bufferedReader() } }
        }
    } else {
        block { name -> source.resolve(name).takeIf { Files.exists(it) }?.toFile()?.reader() }
    }
