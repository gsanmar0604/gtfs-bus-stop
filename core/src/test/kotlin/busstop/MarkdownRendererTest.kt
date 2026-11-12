package busstop

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownRendererTest {

    private fun departure(line: String, headsign: String, time: String) =
        Departure(line, headsign, LocalDateTime.parse(time))

    @Test
    fun `renders departures as a Markdown table`() {
        val markdown = renderMarkdown(
            "Plaza Eliptica",
            listOf(
                departure("L1", "Centro", "2026-11-12T08:00"),
                departure("L2", "Basurto", "2026-11-12T08:30"),
            ),
        )

        val expected = """
            ## Next departures at Plaza Eliptica

            | Time | Line | Destination |
            |------|------|-------------|
            | 08:00 | L1 | Centro |
            | 08:30 | L2 | Basurto |
        """.trimIndent() + "\n"

        assertEquals(expected, markdown)
    }

    @Test
    fun `shows a departure after midnight with its real clock time`() {
        val markdown = renderMarkdown(
            "Plaza Eliptica",
            listOf(departure("L2", "Basurto", "2026-11-13T01:10")),
        )

        assertEquals(true, markdown.contains("| 01:10 | L2 | Basurto |"))
    }

    @Test
    fun `says so when there are no departures`() {
        val markdown = renderMarkdown("Plaza Eliptica", emptyList())

        val expected = """
            ## Next departures at Plaza Eliptica

            No upcoming departures.
        """.trimIndent() + "\n"

        assertEquals(expected, markdown)
    }
}
