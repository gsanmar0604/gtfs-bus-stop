package busstop

import kotlin.test.Test
import kotlin.test.assertEquals

class CsvTest {

    private fun parse(csv: String): List<Map<String, String>> =
        parseCsv(csv.reader()).toList()

    @Test
    fun `parses a simple two-column file`() {
        val rows = parse("id,name\n1,Alice\n2,Bob")
        assertEquals(
            listOf(
                mapOf("id" to "1", "name" to "Alice"),
                mapOf("id" to "2", "name" to "Bob"),
            ),
            rows,
        )
    }

    @Test
    fun `strips a UTF-8 BOM from the header line`() {
        val rows = parse("﻿id,name\n1,Alice")
        assertEquals("1", rows[0]["id"])
    }

    @Test
    fun `trims spaces from header names`() {
        // Some feeds use "id, name" with a space after the comma in the header.
        val rows = parse("id, name\n1,Alice")
        assertEquals("Alice", rows[0]["name"])
    }

    @Test
    fun `handles quoted fields containing commas`() {
        val rows = parse("id,name\n1,\"Plaza Mayor, Bilbao\"")
        assertEquals("Plaza Mayor, Bilbao", rows[0]["name"])
    }

    @Test
    fun `handles escaped double-quotes inside quoted fields`() {
        val rows = parse("id,val\n1,\"a\"\"b\"")
        assertEquals("a\"b", rows[0]["val"])
    }

    @Test
    fun `skips blank lines`() {
        val rows = parse("id,name\n\n1,Alice\n\n2,Bob\n")
        assertEquals(2, rows.size)
    }

    @Test
    fun `empty input returns empty sequence`() {
        assertEquals(emptyList(), parse(""))
    }
}
