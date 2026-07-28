package com.mdview.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mdview.DashboardTab
import com.mdview.R
import com.mdview.data.LibraryEntry
import com.mdview.ui.theme.LocalSkin

/**
 * The app's launcher: a list of documents, with the tabs alongside or underneath.
 *
 * The selected [tab] is hoisted into the ViewModel rather than remembered here, because
 * changing the language recreates the Activity — a `remember` would drop the user back
 * on Recent, which reads as a crash.
 *
 * Past [RAIL_BREAKPOINT] the tabs move to a rail on the leading edge. The switch uses
 * [BoxWithConstraints] rather than a window-size-class library: the answer needed here is
 * "how wide is this container", which is exactly what the constraints already say, and it
 * keeps the dependency list where it is.
 *
 * **Only one of the rail and the bar is ever composed**, which is what lets both carry
 * `DashboardTags.tab(...)` without an `onNodeWithTag` lookup matching two nodes.
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
    val recentScrollState = rememberLazyListState()
    val favoritesScrollState = rememberLazyListState()
    val skin = LocalSkin.current

    // Back from a secondary tab returns to Recent before it leaves the app -- the
    // standard behaviour for a bottom bar, and it keeps an accidental tap from exiting.
    BackHandler(enabled = tab != DashboardTab.Recent) { onSelectTab(DashboardTab.Recent) }

    BoxWithConstraints(modifier) {
        val wide = maxWidth >= RAIL_BREAKPOINT

        Row(Modifier.fillMaxSize()) {
            if (wide) {
                DashboardRail(
                    tab = tab,
                    showActions = tab != DashboardTab.Mine,
                    onSelectTab = onSelectTab,
                    onOpenPicker = onOpenPicker,
                    onNewDocument = onNewDocument,
                )
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(titleOf(tab))) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            // The canvas, not the surface: cards then read as raised
                            // against the bar instead of merging into it.
                            containerColor = skin.colors.canvas,
                            titleContentColor = skin.colors.textPrimary,
                        ),
                    )
                },
                floatingActionButton = {
                    if (!wide && tab != DashboardTab.Mine) {
                        AddDocumentButton(onOpenPicker, onNewDocument)
                    }
                },
                bottomBar = {
                    if (!wide) DashboardBottomBar(tab, onSelectTab)
                },
                containerColor = skin.colors.canvas,
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
                            ListEmptyState(
                                R.string.no_favorites_title,
                                R.string.no_favorites_body,
                                content,
                            )
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
    }
}

@Composable
private fun DashboardBottomBar(tab: DashboardTab, onSelectTab: (DashboardTab) -> Unit) {
    val skin = LocalSkin.current
    NavigationBar(containerColor = skin.colors.surface) {
        DashboardTab.entries.forEach { entry ->
            NavigationBarItem(
                selected = entry == tab,
                onClick = { onSelectTab(entry) },
                icon = { Icon(iconOf(entry, entry == tab), contentDescription = null) },
                label = { Text(stringResource(titleOf(entry))) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = skin.colors.accent,
                    selectedTextColor = skin.colors.textPrimary,
                    indicatorColor = skin.colors.accentSubtle,
                    unselectedIconColor = skin.colors.textMuted,
                    unselectedTextColor = skin.colors.textMuted,
                ),
                modifier = Modifier.testTag(DashboardTags.tab(entry.name)),
            )
        }
    }
}

/** The wide-window tabs. Carries the add action in its header, where the FAB would be. */
@Composable
private fun DashboardRail(
    tab: DashboardTab,
    showActions: Boolean,
    onSelectTab: (DashboardTab) -> Unit,
    onOpenPicker: () -> Unit,
    onNewDocument: () -> Unit,
) {
    val skin = LocalSkin.current
    NavigationRail(
        containerColor = skin.colors.surface,
        header = { if (showActions) AddDocumentButton(onOpenPicker, onNewDocument) },
    ) {
        DashboardTab.entries.forEach { entry ->
            NavigationRailItem(
                selected = entry == tab,
                onClick = { onSelectTab(entry) },
                icon = { Icon(iconOf(entry, entry == tab), contentDescription = null) },
                label = { Text(stringResource(titleOf(entry))) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = skin.colors.accent,
                    selectedTextColor = skin.colors.textPrimary,
                    indicatorColor = skin.colors.accentSubtle,
                    unselectedIconColor = skin.colors.textMuted,
                    unselectedTextColor = skin.colors.textMuted,
                ),
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .testTag(DashboardTags.tab(entry.name)),
            )
        }
    }
}

@Composable
private fun AddDocumentButton(onOpenPicker: () -> Unit, onNewDocument: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val skin = LocalSkin.current

    Box {
        FloatingActionButton(
            onClick = { menuOpen = true },
            containerColor = skin.colors.accent,
            contentColor = skin.colors.onAccent,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.add_document),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.open_file)) },
                leadingIcon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onOpenPicker()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.new_document)) },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = null)
                },
                onClick = {
                    menuOpen = false
                    onNewDocument()
                },
            )
        }
    }
}

private fun titleOf(tab: DashboardTab): Int = when (tab) {
    DashboardTab.Recent -> R.string.tab_recent
    DashboardTab.Favorites -> R.string.tab_favorites
    DashboardTab.Mine -> R.string.tab_mine
}

/** Outlined when idle, filled when selected -- the current idiom for a nav item. */
private fun iconOf(tab: DashboardTab, selected: Boolean): ImageVector = when (tab) {
    DashboardTab.Recent -> Icons.Outlined.History
    DashboardTab.Favorites -> if (selected) Icons.Filled.Star else Icons.Outlined.StarBorder
    DashboardTab.Mine -> Icons.Outlined.Tune
}

/**
 * Where the tabs move from the bottom to the side.
 *
 * 640 dp rather than the usual 600: at exactly 600 the list column plus the rail leaves
 * cards narrower than they are on a large phone, which looks like a regression.
 */
private val RAIL_BREAKPOINT = 640.dp
