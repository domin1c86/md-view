package com.mdview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mdview.markdown.MarkdownBlock
import com.mdview.markdown.MarkdownParser
import com.mdview.markdown.children
import com.mdview.ui.theme.LocalSkin

/**
 * Renders [source] as formatted Markdown.
 *
 * Top-level blocks go into a `LazyColumn` so a long document only composes what is
 * on screen, and the parse is cached until the source actually changes.
 *
 * [scrollState] is hoisted so that switching to the source editor and back returns the
 * reader to where they were rather than to the top of the document.
 */
@Composable
fun PreviewScreen(
    source: String,
    modifier: Modifier = Modifier,
    scrollState: LazyListState = rememberLazyListState(),
) {
    val blocks = remember(source) { MarkdownParser.parse(source).children() }
    val skin = LocalSkin.current

    SelectionContainer(modifier = modifier.fillMaxSize().background(skin.colors.canvas)) {
        // Prose stops being readable much past 70 characters a line, so on a tablet the
        // column is centred and capped rather than stretched across the whole window.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = scrollState,
                modifier = Modifier.widthIn(max = MAX_READING_WIDTH).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(blocks.size) { index -> MarkdownBlock(blocks[index]) }
            }
        }
    }
}

private val MAX_READING_WIDTH = 720.dp
