package com.mdview.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.mdview.R
import org.commonmark.ext.front.matter.YamlFrontMatterBlock
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.ListBlock
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak

/** Nesting level of the list currently being drawn; drives the bullet glyph. */
private val LocalListDepth: ProvidableCompositionLocal<Int> = compositionLocalOf { 0 }

private val BulletGlyphs = listOf("•", "◦", "▪")

/**
 * Renders every top-level block of [root] into a [Column].
 *
 * Callers that expect long documents should instead iterate `root.children()`
 * themselves and feed the nodes to a `LazyColumn` with [MarkdownBlock].
 */
@Composable
fun MarkdownDocument(root: Node, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(BlockSpacing),
    ) {
        root.children().forEach { MarkdownBlock(it) }
    }
}

/** Renders a single block-level node and everything beneath it. */
@Composable
fun MarkdownBlock(node: Node, modifier: Modifier = Modifier) {
    when (node) {
        is Heading -> MarkdownHeading(node, modifier)
        is Paragraph -> {
            // A paragraph that holds nothing but images is a figure, not a sentence.
            val images = node.asImageBlock()
            if (images != null) MarkdownImages(images, modifier) else MarkdownText(node, modifier)
        }

        is BulletList -> MarkdownList(node, modifier)
        is OrderedList -> MarkdownList(node, modifier)
        is BlockQuote -> MarkdownBlockQuote(node, modifier)
        is FencedCodeBlock -> CodeBlock(node.literal, node.info, modifier)
        is IndentedCodeBlock -> CodeBlock(node.literal, info = null, modifier = modifier)
        is TableBlock -> MarkdownTable(node, modifier)
        is ThematicBreak -> HorizontalDivider(modifier.fillMaxWidth().padding(vertical = 8.dp))
        // Raw HTML is shown verbatim rather than silently dropped, so nothing in
        // the source goes missing from the reader's view.
        is HtmlBlock -> CodeBlock(node.literal, info = null, modifier = modifier)
        // Metadata for other tools, not content. Drawing nothing is the whole point of
        // parsing it -- untangled from the document, it can no longer fake a heading.
        is YamlFrontMatterBlock -> Unit
        else -> MarkdownChildBlocks(node, modifier)
    }
}

/**
 * The images in this paragraph if that is all it contains, otherwise null.
 *
 * Images sitting inside a sentence stay inline as an `[image: alt]` placeholder --
 * laying a real one out mid-paragraph means [androidx.compose.foundation.text.InlineTextContent]
 * with a size known before the image has loaded, which is not worth it here.
 */
private fun Paragraph.asImageBlock(): List<Image>? {
    val images = mutableListOf<Image>()
    forEachChild { child ->
        when {
            child is Image -> images += child
            child is SoftLineBreak -> Unit
            child is Text && child.literal.isBlank() -> Unit
            else -> return null
        }
    }
    return images.ifEmpty { null }
}

@Composable
private fun MarkdownChildBlocks(parent: Node, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(BlockSpacing),
    ) {
        parent.children().forEach { MarkdownBlock(it) }
    }
}

@Composable
private fun MarkdownHeading(heading: Heading, modifier: Modifier = Modifier) {
    val typography = MaterialTheme.typography
    val style = when (heading.level) {
        1 -> typography.headlineMedium
        2 -> typography.headlineSmall
        3 -> typography.titleLarge
        4 -> typography.titleMedium
        5 -> typography.titleSmall
        else -> typography.titleSmall
    }.copy(fontWeight = FontWeight.Bold)

    val color = if (heading.level >= 6) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(modifier = modifier.padding(top = 8.dp)) {
        MarkdownText(heading, style = style, color = color)
        // A rule under the top two levels gives long documents a visible spine.
        if (heading.level <= 2) {
            HorizontalDivider(Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun MarkdownText(
    parent: Node,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
    textAlign: TextAlign? = null,
) {
    val styles = rememberInlineStyles()
    val text = remember(parent, styles) { renderInlines(parent, styles) }
    Text(text = text, modifier = modifier, style = style, color = color, textAlign = textAlign)
}

@Composable
private fun MarkdownList(list: ListBlock, modifier: Modifier = Modifier) {
    val depth = LocalListDepth.current
    val items = list.children()
    val startNumber = (list as? OrderedList)?.markerStartNumber ?: 1
    val bullet = BulletGlyphs[depth % BulletGlyphs.size]

    Column(
        modifier = modifier,
        // A tight list is one the author wrote without blank lines; keeping it
        // compact preserves that intent.
        verticalArrangement = Arrangement.spacedBy(if (list.isTight) 2.dp else BlockSpacing),
    ) {
        items.forEachIndexed { index, item ->
            val marker = if (list is OrderedList) "${startNumber + index}." else bullet
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = marker,
                    modifier = Modifier.widthIn(min = 28.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CompositionLocalProvider(LocalListDepth provides depth + 1) {
                    MarkdownChildBlocks(item, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MarkdownBlockQuote(quote: BlockQuote, modifier: Modifier = Modifier) {
    Row(modifier = modifier.height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        )
        MarkdownChildBlocks(quote, Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun CodeBlock(literal: String, info: String?, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        if (!info.isNullOrBlank()) {
            Text(
                text = info.trim().substringBefore(' '),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Text(
            // Code lines must not reflow, so the block scrolls sideways instead.
            text = literal.trimEnd('\n'),
            modifier = Modifier.horizontalScroll(scrollState),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = MonospaceFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            softWrap = false,
        )
    }
}

@Composable
private fun MarkdownImages(images: List<Image>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        images.forEach { MarkdownImage(it) }
    }
}

@Composable
private fun MarkdownImage(image: Image) {
    val alt = collectText(image).ifBlank { image.destination.orEmpty() }

    // Fetching a remote image tells its host that this document was opened, and when --
    // ordinary tracking-pixel behaviour. When the user has asked us not to, say so
    // rather than quietly rendering nothing.
    if (isRemoteImage(image.destination) && LocalRemoteImages.current == RemoteImageAccess.Blocked) {
        ImageNotice(stringResource(R.string.image_blocked, alt))
        return
    }

    SubcomposeAsyncImage(
        // Only absolute references resolve. A document opened through the picker grants
        // access to itself, not its folder, so a relative path has no base to hang off.
        model = image.destination,
        contentDescription = alt.ifBlank { null },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Fit,
        alignment = Alignment.Center,
        loading = { ImageNotice(stringResource(R.string.image_loading)) },
        error = { ImageNotice(stringResource(R.string.image_failed, alt)) },
    )
}

/** Stands in for an image that is still arriving, or never will. */
@Composable
private fun ImageNotice(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 24.dp, horizontal = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun MarkdownTable(table: TableBlock, modifier: Modifier = Modifier) {
    // TableHead / TableBody wrap the rows; flatten them into a single row list.
    val rows = buildList {
        table.children().forEach { section -> addAll(section.children().filterIsInstance<TableRow>()) }
    }
    if (rows.isEmpty()) return

    val outline = MaterialTheme.colorScheme.outline
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        rows.forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) HorizontalDivider(color = outline.copy(alpha = 0.4f))
            val cells = row.children().filterIsInstance<TableCell>()
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                verticalAlignment = Alignment.Top,
            ) {
                cells.forEachIndexed { cellIndex, cell ->
                    if (cellIndex > 0) {
                        Box(Modifier.width(1.dp).fillMaxHeight().background(outline.copy(alpha = 0.4f)))
                    }
                    // Columns share the width evenly; cell text wraps rather than
                    // forcing the whole table to scroll.
                    MarkdownText(
                        parent = cell,
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium.let {
                            if (cell.isHeader) it.copy(fontWeight = FontWeight.Bold) else it
                        },
                        textAlign = when (cell.alignment) {
                            TableCell.Alignment.CENTER -> TextAlign.Center
                            TableCell.Alignment.RIGHT -> TextAlign.End
                            else -> TextAlign.Start
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberInlineStyles(): InlineStyles {
    val colors = MaterialTheme.colorScheme
    val imageTemplate = stringResource(R.string.image_placeholder)
    return remember(colors, imageTemplate) {
        InlineStyles(
            code = SpanStyle(
                fontFamily = MonospaceFamily,
                fontSize = 14.sp,
                background = colors.surfaceVariant,
                color = colors.onSurfaceVariant,
            ),
            link = TextLinkStyles(
                style = SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline),
                pressedStyle = SpanStyle(
                    color = colors.primary,
                    background = colors.primary.copy(alpha = 0.12f),
                    textDecoration = TextDecoration.Underline,
                ),
            ),
            image = SpanStyle(color = colors.onSurfaceVariant, fontStyle = FontStyle.Italic),
            imageLabel = { alt -> imageTemplate.format(alt) },
        )
    }
}

private val BlockSpacing = 12.dp
