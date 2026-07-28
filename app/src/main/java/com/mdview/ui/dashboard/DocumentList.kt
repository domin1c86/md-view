package com.mdview.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mdview.data.LibraryEntry
import com.mdview.ui.CenteredNotice

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
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
    CenteredNotice(
        title = stringResource(titleRes),
        body = stringResource(bodyRes),
        modifier = modifier,
    )
}
