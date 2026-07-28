package com.mdview.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSummaryTest {

    @Test
    fun `the first heading becomes the title and the first paragraph the excerpt`() {
        val summary = DocumentSummary.of("# Release notes\n\nEverything that changed.\n")

        assertEquals("Release notes", summary.title)
        assertEquals("Everything that changed.", summary.excerpt)
    }

    @Test
    fun `inline code inside a heading is kept`() {
        // collectText only appends Text literals, and Code carries its content in
        // `literal` with no children -- so the interesting word would go missing.
        val summary = DocumentSummary.of("# How `parse` works\n\nIt walks the AST.\n")

        assertEquals("How parse works", summary.title)
    }

    @Test
    fun `inline code inside the excerpt is kept`() {
        val summary = DocumentSummary.of("Call `MarkdownParser.parse` first.\n")

        assertEquals("Call MarkdownParser.parse first.", summary.excerpt)
    }

    @Test
    fun `formatting markers are stripped`() {
        val summary = DocumentSummary.of("## **Bold** and *italic*\n\nSome ~~struck~~ text.\n")

        assertEquals("Bold and italic", summary.title)
        assertEquals("Some struck text.", summary.excerpt)
    }

    @Test
    fun `front matter does not become the excerpt`() {
        val source = """
            ---
            title: From metadata
            tags: [a, b]
            ---

            # Real heading

            The actual body.
        """.trimIndent()

        val summary = DocumentSummary.of(source)

        // The front-matter title wins over the heading -- it is the more deliberate
        // statement of what the document is called.
        assertEquals("From metadata", summary.title)
        assertEquals("The actual body.", summary.excerpt)
    }

    @Test
    fun `a quoted front matter title loses its quotes`() {
        val summary = DocumentSummary.of("---\ntitle: \"Quoted name\"\n---\n\nBody.\n")

        assertEquals("Quoted name", summary.title)
    }

    @Test
    fun `front matter without a title falls back to the heading`() {
        val summary = DocumentSummary.of("---\ntags: [a]\n---\n\n# Heading wins\n\nBody.\n")

        assertEquals("Heading wins", summary.title)
    }

    @Test
    fun `a document with no heading has no title`() {
        val summary = DocumentSummary.of("Just a paragraph, nothing else.\n")

        assertNull(summary.title)
        assertEquals("Just a paragraph, nothing else.", summary.excerpt)
    }

    @Test
    fun `an empty document summarises to nothing`() {
        val summary = DocumentSummary.of("")

        assertNull(summary.title)
        assertEquals("", summary.excerpt)
    }

    @Test
    fun `a heading with no body still yields a title`() {
        val summary = DocumentSummary.of("# Only a heading\n")

        assertEquals("Only a heading", summary.title)
        assertEquals("", summary.excerpt)
    }

    @Test
    fun `line breaks inside the excerpt collapse to spaces`() {
        val summary = DocumentSummary.of("First line\nsecond line\n\nAnother paragraph.\n")

        assertEquals("First line second line", summary.excerpt)
    }

    @Test
    fun `a list can serve as the excerpt when there is no paragraph`() {
        val summary = DocumentSummary.of("# Tasks\n\n- first item\n- second item\n")

        assertTrue(summary.excerpt.contains("first item"))
    }

    @Test
    fun `a huge document is not parsed in full`() {
        val source = "# Title\n\nThe excerpt.\n\n" + "filler paragraph\n\n".repeat(200_000)

        val summary = DocumentSummary.of(source)

        assertEquals("Title", summary.title)
        assertEquals("The excerpt.", summary.excerpt)
    }

    @Test
    fun `the excerpt is capped`() {
        val summary = DocumentSummary.of("A ".repeat(5_000))

        assertTrue("was ${summary.excerpt.length}", summary.excerpt.length <= 240)
    }
}
