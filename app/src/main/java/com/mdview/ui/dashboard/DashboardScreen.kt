package com.mdview.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.mdview.DashboardTab
import com.mdview.R
import com.mdview.data.LibraryEntry

/**
 * The app's launcher: a list of documents with a three-tab bar underneath.
 *
 * The selected [tab] is hoisted into the ViewModel rather than remembered here, because
 * changing the language recreates the Activity — a `remember` would drop the user back
 * on Recent, which reads as a crash.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    tab: DashboardTab,
    entries: List<LibraryEntry>,
    reachable: Set<String>,
    hasDraft: Boolean,
    onSelectTab: (DashboardTab) -> Unit,
    onOpenPicker: () -> Unit,
    onNewDocument: () -> Unit,
    onOpenDraft: () -> Unit,
    onOpen: (LibraryEntry) -> Unit,
    onToggleFavorite: (LibraryEntry) -> Unit,
    onForget: (LibraryEntry) -> Unit,
    settingsContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    var fabMenuOpen by remember { mutableStateOf(false) }
    val recentScrollState = rememberLazyListState()
    val favoritesScrollState = rememberLazyListState()

    // Back from a secondary tab returns to Recent before it leaves the app -- the
    // standard behaviour for a bottom bar, and it keeps an accidental tap from exiting.
    BackHandler(enabled = tab != DashboardTab.Recent) { onSelectTab(DashboardTab.Recent) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleOf(tab))) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            if (tab != DashboardTab.Mine) {
                Box {
                    FloatingActionButton(onClick = { fabMenuOpen = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.add_document),
                        )
                    }
                    DropdownMenu(
                        expanded = fabMenuOpen,
                        onDismissRequest = { fabMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.open_file)) },
                            leadingIcon = {
                                Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                            },
                            onClick = {
                                fabMenuOpen = false
                                onOpenPicker()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_document)) },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = null)
                            },
                            onClick = {
                                fabMenuOpen = false
                                onNewDocument()
                            },
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                DashboardTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = entry == tab,
                        onClick = { onSelectTab(entry) },
                        icon = { Icon(iconOf(entry), contentDescription = null) },
                        label = { Text(stringResource(titleOf(entry))) },
                        modifier = Modifier.testTag(DashboardTags.tab(entry.name)),
                    )
                }
            }
        },
    ) { innerPadding ->
        val content = Modifier.fillMaxSize().padding(innerPadding)
        when (tab) {
            DashboardTab.Recent -> if (entries.isEmpty() && !hasDraft) {
                ListEmptyState(R.string.no_recent_title, R.string.no_recent_body, content)
            } else {
                DocumentList(
                    entries = entries,
                    reachable = reachable,
                    onOpen = onOpen,
                    onToggleFavorite = onToggleFavorite,
                    onForget = onForget,
                    scrollState = recentScrollState,
                    modifier = content,
                    header = if (hasDraft) {
                        { DraftCard(onOpen = onOpenDraft) }
                    } else {
                        null
                    },
                )
            }

            DashboardTab.Favorites -> {
                val favorites = entries.filter { it.isFavorite }
                if (favorites.isEmpty()) {
                    ListEmptyState(R.string.no_favorites_title, R.string.no_favorites_body, content)
                } else {
                    DocumentList(
                        entries = favorites,
                        reachable = reachable,
                        onOpen = onOpen,
                        onToggleFavorite = onToggleFavorite,
                        onForget = onForget,
                        scrollState = favoritesScrollState,
                        modifier = content,
                    )
                }
            }

            DashboardTab.Mine -> settingsContent(content)
        }
    }
}

private fun titleOf(tab: DashboardTab): Int = when (tab) {
    DashboardTab.Recent -> R.string.tab_recent
    DashboardTab.Favorites -> R.string.tab_favorites
    DashboardTab.Mine -> R.string.tab_mine
}

private fun iconOf(tab: DashboardTab): ImageVector = when (tab) {
    DashboardTab.Recent -> Icons.Outlined.Schedule
    DashboardTab.Favorites -> Icons.Filled.Star
    DashboardTab.Mine -> Icons.Outlined.Person
}
