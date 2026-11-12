package busstop

import java.io.Reader

private const val BOM = '﻿'

/**
 * Parses a CSV reader into a sequence of rows, each row a map of header → value.
 * Handles: UTF-8 BOM, RFC 4180 quoting (commas and escaped quotes inside fields),
 * trimmed header names, and blank lines.
 */
fun parseCsv(reader: Reader): Sequence<Map<String, String>> = sequence {
    val iter = reader.buffered().lineSequence().iterator()
    if (!iter.hasNext()) return@sequence

    val headerLine = iter.next().trimStart(BOM)
    if (headerLine.isBlank()) return@sequence
    val headers = splitCsvLine(headerLine).map { it.trim() }

    for (line in iter) {
        if (line.isBlank()) continue
        val values = splitCsvLine(line)
        yield(headers.zip(values).toMap())
    }
}

private fun splitCsvLine(line: String): List<String> {
    val fields = mutableListOf<String>()
    var i = 0

    while (i < line.length) {
        if (line[i] == '"') {
            val sb = StringBuilder()
            i++ // skip opening quote
            while (i < line.length) {
                when {
                    line[i] == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                        sb.append('"'); i += 2 // escaped double-quote
                    }
                    line[i] == '"' -> { i++; break } // closing quote
                    else -> sb.append(line[i++])
                }
            }
            fields.add(sb.toString())
        } else {
            val start = i
            while (i < line.length && line[i] != ',') i++
            fields.add(line.substring(start, i))
        }
        if (i < line.length && line[i] == ',') i++ else break
    }

    // A trailing comma means one more empty field.
    if (line.isNotEmpty() && line.last() == ',') fields.add("")
    return fields
}
