package com.mdview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mdview.markdown.MarkdownBlock
import com.mdview.markdown.MarkdownParser
import com.mdview.markdown.children

/**
 * Renders [source] as formatted Markdown.
 *
 * Top-level blocks go into a `LazyColumn` so a long document only composes what is
 * on screen, and the parse is cached until the source actually changes.
 */
@Composable
fun PreviewScreen(source: String, modifier: Modifier = Modifier) {
    val blocks = remember(source) { MarkdownParser.parse(source).children() }

    SelectionContainer(modifier = modifier) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(blocks.size) { index -> MarkdownBlock(blocks[index]) }
        }
    }
}
