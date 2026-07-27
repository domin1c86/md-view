package com.mdview.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.HardLineBreak
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text

/**
 * Styling the inline renderer needs. Kept as plain data so the conversion itself
 * stays outside composition and can be unit tested on the JVM.
 */
data class InlineStyles(
    val code: SpanStyle,
    val link: TextLinkStyles,
    val image: SpanStyle,
    /** Renders the placeholder shown in place of an image, e.g. `[image: logo]`. */
    val imageLabel: (alt: String) -> String,
)

/**
 * Flattens the inline children of [parent] into an [AnnotatedString].
 *
 * Links become real [LinkAnnotation.Url] annotations, so `Text` handles the click
 * and hands the URL to the system browser without any extra wiring.
 */
fun renderInlines(parent: Node, styles: InlineStyles): AnnotatedString =
    buildAnnotatedString { appendInlines(parent, styles) }

private fun AnnotatedString.Builder.appendInlines(parent: Node, styles: InlineStyles) {
    parent.forEachChild { child ->
        when (child) {
            is Text -> append(child.literal)

            is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendInlines(child, styles)
            }

            is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendInlines(child, styles)
            }

            is Strikethrough -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                appendInlines(child, styles)
            }

            is Code -> withStyle(styles.code) { append(child.literal) }

            is Link -> {
                val destination = child.destination
                if (destination.isNullOrBlank()) {
                    appendInlines(child, styles)
                } else {
                    withLink(LinkAnnotation.Url(destination, styles.link)) {
                        appendInlines(child, styles)
                    }
                }
            }

            // Images are not fetched in this release; show the alt text instead so
            // the reader still knows something belongs here.
            is Image -> withStyle(styles.image) {
                append(styles.imageLabel(collectText(child).ifBlank { child.destination.orEmpty() }))
            }

            // CommonMark renders a soft break as a space; only a hard break wraps.
            is SoftLineBreak -> append(' ')
            is HardLineBreak -> append('\n')

            // Raw HTML tags are dropped, but their text content is kept.
            is HtmlInline -> Unit

            else -> appendInlines(child, styles)
        }
    }
}

/** Concatenates every [Text] literal beneath [node], ignoring formatting. */
internal fun collectText(node: Node): String = buildString {
    fun walk(current: Node) {
        current.forEachChild { child ->
            if (child is Text) append(child.literal) else walk(child)
        }
    }
    walk(node)
}

internal val MonospaceFamily: FontFamily = FontFamily.Monospace
