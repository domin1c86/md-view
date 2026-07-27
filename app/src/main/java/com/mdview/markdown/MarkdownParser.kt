package com.mdview.markdown

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.front.matter.YamlFrontMatterExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.Node
import org.commonmark.parser.Parser

/**
 * CommonMark parsing, with the GitHub-flavoured extensions the renderer knows how
 * to draw. The [Parser] is immutable and safe to share across threads.
 */
object MarkdownParser {

    private val parser: Parser = Parser.builder()
        .extensions(
            listOf(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                AutolinkExtension.create(),
                // Without this, a `---` metadata block is read as a thematic break
                // followed by a setext heading, so every Obsidian or Hugo document
                // opens with a bogus title. The renderer draws nothing for it.
                YamlFrontMatterExtension.create(),
            )
        )
        .build()

    fun parse(source: String): Node = parser.parse(source)
}

/** Iterates a node's direct children; the AST is a linked list, not a collection. */
internal inline fun Node.forEachChild(action: (Node) -> Unit) {
    var child = firstChild
    while (child != null) {
        // Read `next` first: the action must stay safe even if it unlinks the node.
        val next = child.next
        action(child)
        child = next
    }
}

internal fun Node.children(): List<Node> = buildList { forEachChild(::add) }
