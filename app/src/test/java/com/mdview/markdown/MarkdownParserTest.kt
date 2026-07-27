package com.mdview.markdown

import org.commonmark.ext.front.matter.YamlFrontMatterBlock
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.ThematicBreak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every construct the preview renderer claims to support, parsed end to end. */
private const val FIXTURE = """
# Title

Intro paragraph with **bold**, *italic*, ~~struck~~ and `code`.

## Section

- first
- second
  - nested
- third

1. one
2. two

> Quoted line.

```kotlin
fun main() = Unit
```

| Left | Center | Right |
|:-----|:------:|------:|
| a    | b      | c     |

---

[A link](https://example.com) and ![alt text](img.png).
"""

class MarkdownParserTest {

    private val root: Node = MarkdownParser.parse(FIXTURE)

    private inline fun <reified T : Node> blocksOfType(): List<T> =
        root.children().filterIsInstance<T>()

    @Test
    fun `headings keep their level`() {
        val headings = blocksOfType<Heading>()
        assertEquals(listOf(1, 2), headings.map { it.level })
        assertEquals("Title", collectText(headings[0]))
    }

    @Test
    fun `bullet list nests sublists inside their item`() {
        val list = blocksOfType<BulletList>().single()
        val items = list.children().filterIsInstance<ListItem>()
        assertEquals(3, items.size)

        val nested = items[1].children().filterIsInstance<BulletList>().single()
        assertEquals("nested", collectText(nested).trim())
    }

    @Test
    fun `ordered list exposes its start number`() {
        val list = blocksOfType<OrderedList>().single()
        assertEquals(1, list.markerStartNumber)
        assertEquals(2, list.children().filterIsInstance<ListItem>().size)
    }

    @Test
    fun `fenced code keeps its language and literal text`() {
        val code = blocksOfType<FencedCodeBlock>().single()
        assertEquals("kotlin", code.info)
        assertEquals("fun main() = Unit\n", code.literal)
    }

    @Test
    fun `block quote and thematic break are present`() {
        assertEquals("Quoted line.", collectText(blocksOfType<BlockQuote>().single()).trim())
        assertEquals(1, blocksOfType<ThematicBreak>().size)
    }

    @Test
    fun `table rows carry header flags and alignment`() {
        val table = blocksOfType<TableBlock>().single()
        val cells = mutableListOf<TableCell>()
        fun walk(node: Node) {
            node.forEachChild { child ->
                if (child is TableCell) cells += child else walk(child)
            }
        }
        walk(table)

        assertEquals(6, cells.size)
        assertTrue(cells.take(3).all { it.isHeader })
        assertFalse(cells.drop(3).any { it.isHeader })
        assertEquals(
            listOf(TableCell.Alignment.LEFT, TableCell.Alignment.CENTER, TableCell.Alignment.RIGHT),
            cells.take(3).map { it.alignment },
        )
    }

    @Test
    fun `paragraphs are found for the prose blocks`() {
        val paragraphs = blocksOfType<Paragraph>()
        assertTrue(paragraphs.any { collectText(it).contains("Intro paragraph") })
        assertTrue(paragraphs.any { collectText(it).contains("A link") })
    }

    @Test
    fun `an empty document parses to no blocks`() {
        assertTrue(MarkdownParser.parse("").children().isEmpty())
    }

    @Test
    fun `front matter is parsed as metadata rather than a heading`() {
        val document = MarkdownParser.parse(
            """
            ---
            title: My note
            tags: [a, b]
            ---

            # Real heading
            """.trimIndent()
        )

        // Unparsed, `---` opens a thematic break and closes a setext heading, so
        // `title: My note` would show up as an H2 above the document's real title.
        val block = document.children().first()
        assertTrue("expected front matter, got ${block::class.java.simpleName}", block is YamlFrontMatterBlock)

        val headings = document.children().filterIsInstance<Heading>()
        assertEquals(listOf("Real heading"), headings.map { collectText(it) })
    }

    @Test
    fun `front matter markers still work as a thematic break mid-document`() {
        val document = MarkdownParser.parse("Some prose.\n\n---\n\nMore prose.\n")

        assertEquals(1, document.children().filterIsInstance<ThematicBreak>().size)
    }
}
