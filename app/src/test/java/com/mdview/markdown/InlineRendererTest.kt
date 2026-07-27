package com.mdview.markdown

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.commonmark.node.Node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineRendererTest {

    private val styles = InlineStyles(
        code = SpanStyle(fontWeight = FontWeight.Medium),
        link = TextLinkStyles(style = SpanStyle(fontWeight = FontWeight.Bold)),
        image = SpanStyle(fontStyle = FontStyle.Italic),
        imageLabel = { alt -> "[image: $alt]" },
    )

    private fun firstBlock(source: String): Node =
        MarkdownParser.parse(source).children().first()

    private fun render(source: String) = renderInlines(firstBlock(source), styles)

    @Test
    fun `formatting markers are stripped from the visible text`() {
        assertEquals(
            "bold italic code struck",
            render("**bold** *italic* `code` ~~struck~~").text,
        )
    }

    @Test
    fun `each formatted run gets its own span`() {
        val result = render("**bold** and *italic*")
        assertEquals(2, result.spanStyles.size)
        assertEquals(FontWeight.Bold, result.spanStyles[0].item.fontWeight)
        assertEquals(FontStyle.Italic, result.spanStyles[1].item.fontStyle)
    }

    @Test
    fun `a link keeps its label and carries a url annotation`() {
        val result = render("see [the docs](https://example.com) now")
        assertEquals("see the docs now", result.text)
        assertEquals(1, result.getLinkAnnotations(0, result.length).size)
    }

    @Test
    fun `an autolink becomes a link without extra text`() {
        val result = render("visit https://example.com today")
        assertEquals("visit https://example.com today", result.text)
        assertEquals(1, result.getLinkAnnotations(0, result.length).size)
    }

    @Test
    fun `images fall back to their alt text`() {
        assertEquals("[image: a diagram]", render("![a diagram](chart.png)").text)
    }

    @Test
    fun `an image without alt text falls back to its destination`() {
        assertEquals("[image: chart.png]", render("![](chart.png)").text)
    }

    @Test
    fun `a soft break joins lines with a space but a hard break wraps`() {
        assertEquals("one two", render("one\ntwo").text)
        assertEquals("one\ntwo", render("one  \ntwo").text)
    }

    @Test
    fun `nested emphasis keeps both styles`() {
        val result = render("***both***")
        assertEquals("both", result.text)
        assertTrue(result.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(result.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
    }

    @Test
    fun `raw html tags are dropped but their content survives`() {
        assertEquals("kept", render("<span>kept</span>").text)
    }
}
