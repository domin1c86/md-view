package com.mdview.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mdview.data.LibraryEntry

/**
 * The scrolling list of document cards shared by Recent and Favourites.
 *
 * [reachable] is the set of URIs this app can still open; anything outside it had its
 * grant revoked or was never persistable, and its card is drawn as unavailable.
 */
@Composable
fun DocumentList(
    entries: List<LibraryEntry>,
    reachable: Set<String>,
    onOpen: (LibraryEntry) -> Unit,
    onToggleFavorite: (LibraryEntry) -> Unit,
    onForget: (LibraryEntry) -> Unit,
    scrollState: LazyListState,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(DashboardTags.CARD_LIST),
        state = scrollState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        header?.let { item(key = "header") { it() } }
        items(entries, key = { it.uri }) { entry ->
            DocumentCard(
                entry = entry,
                // An empty set means the check has not run yet; assuming everything is
                // broken would grey out the whole list on every launch.
                unavailable = reachable.isNotEmpty() && entry.uri !in reachable,
                onOpen = { onOpen(entry) },
                onToggleFavorite = { onToggleFavorite(entry) },
                onForget = { onForget(entry) },
            )
        }
    }
}

/** Shown when a tab has nothing in it, so neither tab is ever a blank page. */
@Composable
fun ListEmptyState(titleRes: Int, bodyRes: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Article,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp).padding(top = 8.dp),
        )
    }
}
