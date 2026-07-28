package com.mdview.markdown

import org.commonmark.ext.front.matter.YamlFrontMatterBlock
import org.commonmark.ext.front.matter.YamlFrontMatterNode
import org.commonmark.node.Code
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text

/**
 * A one-line heading and excerpt for a document, used to draw its card on the dashboard.
 *
 * Pure Kotlin so it can be unit tested; lives in this package because [children] and
 * [forEachChild] are internal to it.
 */
data class DocumentSummary(val title: String?, val excerpt: String) {

    companion object {
        /**
         * Documents may be up to 2 MB (`DocumentRepository.MAX_SIZE_BYTES`) and this runs
         * on every open and save. Only the head of the file can contain the first heading
         * and first paragraph, so parsing the rest is wasted work.
         */
        private const val SCAN_LENGTH = 4 * 1024
        private const val MAX_EXCERPT = 240

        /**
         * Call from a background dispatcher — this parses Markdown.
         */
        fun of(source: String): DocumentSummary {
            val head = source.take(SCAN_LENGTH)
            val blocks = MarkdownParser.parse(head).children()

            val frontMatterTitle = blocks.filterIsInstance<YamlFrontMatterBlock>()
                .firstNotNullOfOrNull(::titleFromFrontMatter)

            // Front matter is a block like any other, so it has to be skipped explicitly
            // or a Hugo document's metadata becomes its excerpt.
            val body = blocks.filterNot { it is YamlFrontMatterBlock }
            val heading = body.filterIsInstance<Heading>().firstOrNull()

            val excerptSource = body.firstOrNull { it !== heading && it !is Heading }
            return DocumentSummary(
                title = (frontMatterTitle ?: heading?.let(::flatten))?.takeIf { it.isNotBlank() },
                excerpt = excerptSource?.let(::flatten).orEmpty().take(MAX_EXCERPT),
            )
        }

        private fun titleFromFrontMatter(block: YamlFrontMatterBlock): String? =
            block.children()
                .filterIsInstance<YamlFrontMatterNode>()
                .firstOrNull { it.key.equals("title", ignoreCase = true) }
                ?.values
                ?.firstOrNull()
                ?.trim()
                ?.trim('"', '\'')
                ?.takeIf { it.isNotBlank() }

        /**
         * Flattens a block to plain text on one line.
         *
         * Deliberately not [collectText]: that only appends [Text] literals and recurses
         * into everything else, but [Code] carries its content in `literal` with no
         * children — so `` # `parse` returns a Node `` would come out as
         * `" returns a Node"` with the interesting word missing.
         *
         * The result is capped by characters rather than lines because the same character
         * count fills wildly different amounts of a card in Chinese and in English; the
         * card's `maxLines` does the visual truncation.
         */
        private fun flatten(node: Node): String = buildString {
            fun walk(current: Node) {
                current.forEachChild { child ->
                    when (child) {
                        is Text -> append(child.literal)
                        is Code -> append(child.literal)
                        is SoftLineBreak, is HardLineBreak -> append(' ')
                        else -> walk(child)
                    }
                }
            }
            walk(node)
        }.replace(WHITESPACE, " ").trim()

        private val WHITESPACE = Regex("\\s+")
    }
}
