package com.mdview.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mdview.R
import com.mdview.data.LibraryFolder
import com.mdview.ui.SkinDropdownMenu
import com.mdview.ui.theme.LocalSkin

/**
 * The folder chips above the Recent list.
 *
 * A **sibling** of the card list rather than one of its items, so it stays put while the
 * cards scroll under it — a header item would carry the folders off the top of the screen
 * exactly when the user is furthest from the document they were looking for.
 *
 * Selecting a chip filters the list below; tapping the selected one clears the filter, as
 * does the back gesture. Long-pressing opens rename and delete.
 *
 * The chips are drawn here rather than with Material's `FilterChip` for the reason
 * `DocumentCard` gives: a hairline and a flat surface, never a shadow. `FilterChip` also
 * resolves its own timing through the `internal` `MotionScheme`, which this app cannot
 * supply.
 */
@Composable
fun FolderStrip(
    folders: List<LibraryFolder>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onCreate: () -> Unit,
    onRename: (LibraryFolder) -> Unit,
    onDelete: (LibraryFolder) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth().testTag(DashboardTags.FOLDER_STRIP),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(folders, key = { it.id }) { folder ->
            FolderChip(
                folder = folder,
                selected = folder.id == selectedId,
                onSelect = onSelect,
                onRename = { onRename(folder) },
                onDelete = { onDelete(folder) },
            )
        }

        // Kept at the cap rather than hidden. A chip that vanishes explains nothing; the
        // dialog it opens says "no room for more folders", which is the answer.
        item(key = "new") { NewFolderChip(onCreate) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderChip(
    folder: LibraryFolder,
    selected: Boolean,
    onSelect: (String?) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(skin.shape.small.dp)
    // Announced as a filter that is on or off, so a screen reader says what tapping does
    // rather than leaving "Work, button" to be guessed at.
    val clearLabel = stringResource(R.string.folder_show_all)

    Box {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(if (selected) skin.colors.accentSubtle else skin.colors.surface)
                .border(
                    BorderStroke(1.dp, if (selected) skin.colors.accent else skin.colors.border),
                    shape,
                )
                .combinedClickable(
                    onClick = { onSelect(folder.id.takeUnless { selected }) },
                    onLongClick = { menuOpen = true },
                )
                .semantics {
                    this.selected = selected
                    role = Role.Tab
                    if (selected) contentDescription = "${folder.name}, $clearLabel"
                }
                .testTag(DashboardTags.folderChip(folder.id))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = if (selected) skin.colors.accent else skin.colors.textMuted,
                modifier = Modifier.padding(end = 6.dp).size(16.dp),
            )
            Text(
                text = folder.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) skin.colors.textPrimary else skin.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        SkinDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.folder_rename)) },
                onClick = {
                    menuOpen = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.folder_delete)) },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun NewFolderChip(onCreate: () -> Unit) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(skin.shape.small.dp)

    Row(
        modifier = Modifier
            .clip(shape)
            .border(BorderStroke(1.dp, skin.colors.border), shape)
            .clickable(onClick = onCreate, role = Role.Button)
            .testTag(DashboardTags.NEW_FOLDER)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = skin.colors.textMuted,
            modifier = Modifier.padding(end = 6.dp).size(16.dp),
        )
        Text(
            text = stringResource(R.string.folder_new),
            style = MaterialTheme.typography.labelLarge,
            color = skin.colors.textSecondary,
            maxLines = 1,
        )
    }
}
