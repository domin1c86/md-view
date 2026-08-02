package com.mdview.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tune
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
import com.mdview.data.LibraryFolder
import com.mdview.ui.SkinDialog
import com.mdview.ui.SkinDropdownMenu
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
    folders: List<LibraryFolder>,
    selectedFolderId: String?,
    reachable: Set<String>,
    hasDraft: Boolean,
    backEnabled: Boolean,
    onSelectTab: (DashboardTab) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onMoveDocument: (LibraryEntry, String?) -> Unit,
    onOpenPicker: (String?) -> Unit,
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

    // The selected folder survives its own chip disappearing (a delete, or a reload that
    // dropped it), so resolve it every time rather than trusting the id alone.
    val selectedFolder = folders.firstOrNull { it.id == selectedFolderId }

    var naming by remember { mutableStateOf<FolderNaming?>(null) }
    var deleting by remember { mutableStateOf<LibraryFolder?>(null) }
    var moving by remember { mutableStateOf<LibraryEntry?>(null) }

    // Back unwinds the dashboard one step at a time -- out of a folder first, then back
    // to Recent from a secondary tab, and only then out of the app. That is the standard
    // behaviour for a bottom bar, and it keeps an accidental tap from exiting.
    //
    // [backEnabled] is false while this screen is animating away, so it cannot claim a
    // back press that belongs to the document arriving over it.
    BackHandler(enabled = backEnabled && (selectedFolder != null || tab != DashboardTab.Recent)) {
        if (selectedFolder != null) onSelectFolder(null) else onSelectTab(DashboardTab.Recent)
    }

    BoxWithConstraints(modifier) {
        val wide = maxWidth >= RAIL_BREAKPOINT

        Row(Modifier.fillMaxSize()) {
            if (wide) {
                DashboardRail(
                    tab = tab,
                    showActions = tab != DashboardTab.Mine,
                    onSelectTab = onSelectTab,
                    onOpenPicker = { onOpenPicker(selectedFolderId) },
                    onNewDocument = onNewDocument,
                )
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        // The folder's name replaces "Recent" while it is filtering, so
                        // the bar says which list is on screen rather than which tab is.
                        title = { Text(selectedFolder?.name ?: stringResource(titleOf(tab))) },
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
                        // Carries the selected folder, which is what "import from inside a
                        // folder" amounts to: the picker is the same, the filing is not.
                        AddDocumentButton({ onOpenPicker(selectedFolderId) }, onNewDocument)
                    }
                },
                bottomBar = {
                    if (!wide) DashboardBottomBar(tab, onSelectTab)
                },
                containerColor = skin.colors.canvas,
            ) { innerPadding ->
                val content = Modifier.fillMaxSize().padding(innerPadding)
                when (tab) {
                    DashboardTab.Recent -> RecentTab(
                        entries = entries,
                        folders = folders,
                        selectedFolder = selectedFolder,
                        reachable = reachable,
                        // The scratch draft belongs to no folder, so it is offered only
                        // on the unfiltered list where it cannot look like a member.
                        hasDraft = hasDraft && selectedFolder == null,
                        onSelectFolder = onSelectFolder,
                        onCreateFolder = { naming = FolderNaming(editing = null) },
                        onRenameFolder = { naming = FolderNaming(editing = it) },
                        onDeleteFolder = { deleting = it },
                        onOpenDraft = onOpenDraft,
                        onOpen = onOpen,
                        onToggleFavorite = onToggleFavorite,
                        onForget = onForget,
                        onMove = { moving = it },
                        scrollState = recentScrollState,
                        modifier = content,
                    )

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
                                onMove = { moving = it },
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

    // Outside the layout because each is its own window, and held as nullable state so
    // they keep animating out after the thing they were about is gone.
    FolderNameDialog(
        visible = naming != null,
        editing = naming?.editing,
        folders = folders,
        onDismissRequest = { naming = null },
        onConfirm = { name ->
            naming?.editing?.let { onRenameFolder(it.id, name) } ?: onCreateFolder(name)
            naming = null
        },
    )

    FolderDeleteDialog(
        folder = deleting,
        onDismissRequest = { deleting = null },
        onConfirm = {
            deleting?.let { onDeleteFolder(it.id) }
            deleting = null
        },
    )

    MoveToFolderDialog(
        visible = moving != null,
        folders = folders,
        current = moving?.folderId,
        onDismissRequest = { moving = null },
        onConfirm = { folderId ->
            moving?.let { onMoveDocument(it, folderId) }
            moving = null
        },
    )
}

/** What the name dialog is currently for: a new folder, or [editing] an existing one. */
private data class FolderNaming(val editing: LibraryFolder?)

/**
 * Recent: the folder chips, then the cards.
 *
 * The strip sits *outside* the [DocumentList] rather than as its header, so it stays put
 * while the cards scroll — a header would carry the folders off the top of the screen
 * exactly when the user is furthest from what they were looking for.
 */
@Composable
private fun RecentTab(
    entries: List<LibraryEntry>,
    folders: List<LibraryFolder>,
    selectedFolder: LibraryFolder?,
    reachable: Set<String>,
    hasDraft: Boolean,
    onSelectFolder: (String?) -> Unit,
    onCreateFolder: () -> Unit,
    onRenameFolder: (LibraryFolder) -> Unit,
    onDeleteFolder: (LibraryFolder) -> Unit,
    onOpenDraft: () -> Unit,
    onOpen: (LibraryEntry) -> Unit,
    onToggleFavorite: (LibraryEntry) -> Unit,
    onForget: (LibraryEntry) -> Unit,
    onMove: (LibraryEntry) -> Unit,
    scrollState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val shown = selectedFolder?.let { folder -> entries.filter { it.folderId == folder.id } }
        ?: entries

    Column(modifier) {
        // Hidden only when there is nothing at all to organise: an empty strip on a fresh
        // install would be a control with no visible purpose.
        if (folders.isNotEmpty() || entries.isNotEmpty()) {
            FolderStrip(
                folders = folders,
                selectedId = selectedFolder?.id,
                onSelect = onSelectFolder,
                onCreate = onCreateFolder,
                onRename = onRenameFolder,
                onDelete = onDeleteFolder,
            )
        }

        val empty = Modifier.fillMaxSize()
        when {
            shown.isNotEmpty() || hasDraft -> DocumentList(
                entries = shown,
                reachable = reachable,
                onOpen = onOpen,
                onToggleFavorite = onToggleFavorite,
                onForget = onForget,
                onMove = onMove,
                scrollState = scrollState,
                modifier = Modifier.fillMaxSize(),
                header = if (hasDraft) {
                    { DraftCard(onOpen = onOpenDraft) }
                } else {
                    null
                },
            )

            selectedFolder != null -> ListEmptyState(
                R.string.no_folder_documents_title,
                R.string.no_folder_documents_body,
                empty,
            )

            else -> ListEmptyState(R.string.no_recent_title, R.string.no_recent_body, empty)
        }
    }
}

/**
 * Confirms deleting a folder.
 *
 * The wording has to keep saying that nothing on the device goes with it — a folder here
 * is a label, and "delete folder" means something far more alarming everywhere else.
 */
@Composable
private fun FolderDeleteDialog(
    folder: LibraryFolder?,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    SkinDialog(
        visible = folder != null,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.folder_delete_title),
        body = stringResource(R.string.folder_delete_body),
        confirmLabel = stringResource(R.string.folder_delete_confirm),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.cancel),
        destructive = true,
    )
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
            // Every rung, not just the default one. Setting `defaultElevation` alone still
            // left the button growing a shadow the moment it was pressed or hovered, which
            // is the one state where the shadow is most visible.
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
            ),
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.add_document),
            )
        }
        SkinDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
