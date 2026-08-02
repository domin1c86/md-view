package com.mdview.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mdview.R
import com.mdview.data.FolderNameProblem
import com.mdview.data.FolderNames
import com.mdview.data.LibraryFolder
import com.mdview.ui.SkinDialogButtons
import com.mdview.ui.SkinPanelDialog
import com.mdview.ui.theme.LocalSkin

/**
 * Naming a folder, for both **New folder** and **Rename**.
 *
 * The rules come from [FolderNames], the same object [com.mdview.data.LibraryStore]
 * enforces them with. Checking them here as well is not duplication but the point: without
 * it a rejected name would close the dialog and quietly do nothing, which is
 * indistinguishable from the folder failing to save.
 *
 * [editing] is the folder being renamed, or null when creating one — it is what stops a
 * rename colliding with its own current name.
 */
@Composable
fun FolderNameDialog(
    visible: Boolean,
    editing: LibraryFolder?,
    folders: List<LibraryFolder>,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val skin = LocalSkin.current
    val nameState = remember { TextFieldState() }
    var showError by remember { mutableStateOf(false) }

    // Reset on the way *in* rather than by keying the state on `visible`, which would
    // blank the field during the exit animation while the panel is still on screen.
    LaunchedEffect(visible, editing) {
        if (!visible) return@LaunchedEffect
        nameState.setTextAndPlaceCursorAtEnd(editing?.name.orEmpty())
        showError = false
    }

    val name = nameState.text.toString()
    val problem = FolderNames.problemWith(name, folders, excluding = editing?.id)
    val error = when {
        problem != null -> messageFor(problem)
        // Being full is not a problem with the *name*, but it is the answer the user
        // needs, and this is the only place they can be told: the chip stays on the strip
        // at the cap precisely so there is somewhere to say it.
        editing == null && FolderNames.isFull(folders) -> R.string.folders_full
        else -> null
    }

    fun submit() {
        if (error != null) {
            showError = true
            return
        }
        onConfirm(name)
    }

    SkinPanelDialog(visible = visible, onDismissRequest = onDismissRequest) {
        Text(
            text = stringResource(
                if (editing != null) R.string.folder_rename_title else R.string.folder_new
            ),
            style = MaterialTheme.typography.titleMedium,
            color = skin.colors.textPrimary,
        )

        FolderNameField(nameState, Modifier.padding(top = 14.dp))

        // Held back until the first attempt: an empty field is not yet a mistake, and
        // greeting the user with "give the folder a name" before they have typed reads
        // as the dialog telling them off for opening it.
        if (showError && error != null) {
            Text(
                text = stringResource(error),
                style = MaterialTheme.typography.labelMedium,
                color = skin.colors.danger,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag(DashboardTags.FOLDER_NAME_ERROR),
            )
        }

        SkinDialogButtons(
            onDismissRequest = onDismissRequest,
            dismissLabel = stringResource(R.string.cancel),
            confirmLabel = stringResource(
                if (editing != null) R.string.folder_rename_confirm else R.string.folder_create_confirm
            ),
            onConfirm = ::submit,
        )
    }
}

@Composable
private fun FolderNameField(state: TextFieldState, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    OutlinedTextField(
        state = state,
        label = { Text(stringResource(R.string.folder_name_hint)) },
        lineLimits = TextFieldLineLimits.SingleLine,
        shape = RoundedCornerShape(skin.shape.small.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = skin.colors.textPrimary,
            unfocusedTextColor = skin.colors.textPrimary,
            focusedBorderColor = skin.colors.accent,
            unfocusedBorderColor = skin.colors.border,
            focusedLabelColor = skin.colors.accent,
            unfocusedLabelColor = skin.colors.textMuted,
            cursorColor = skin.colors.accent,
        ),
        modifier = modifier.fillMaxWidth().testTag(DashboardTags.FOLDER_NAME_FIELD),
    )
}

/**
 * Filing one document, as a single choice including **No folder**.
 *
 * A radio list rather than more items on the card's dropdown: with twenty folders that
 * menu would be longer than the screen, and the current folder would be invisible in it.
 */
@Composable
fun MoveToFolderDialog(
    visible: Boolean,
    folders: List<LibraryFolder>,
    current: String?,
    onDismissRequest: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    val skin = LocalSkin.current
    var chosen by remember(current, visible) { mutableStateOf(current) }

    SkinPanelDialog(visible = visible, onDismissRequest = onDismissRequest) {
        Text(
            text = stringResource(R.string.folder_move_title),
            style = MaterialTheme.typography.titleMedium,
            color = skin.colors.textPrimary,
        )

        Column(
            modifier = Modifier
                .padding(top = 8.dp)
                // Twenty folders will not fit on a short screen, and the buttons below
                // must never be pushed off it.
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState())
                .selectableGroup(),
        ) {
            FolderChoice(
                label = stringResource(R.string.folder_move_none),
                selected = chosen == null,
                id = null,
                onSelect = { chosen = null },
            )
            folders.forEach { folder ->
                FolderChoice(
                    label = folder.name,
                    selected = chosen == folder.id,
                    id = folder.id,
                    onSelect = { chosen = folder.id },
                )
            }
        }

        SkinDialogButtons(
            onDismissRequest = onDismissRequest,
            dismissLabel = stringResource(R.string.cancel),
            confirmLabel = stringResource(R.string.folder_move_confirm),
            onConfirm = { onConfirm(chosen) },
        )
    }
}

@Composable
private fun FolderChoice(
    label: String,
    selected: Boolean,
    id: String?,
    onSelect: () -> Unit,
) {
    val skin = LocalSkin.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .testTag(DashboardTags.folderChoice(id))
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = skin.colors.accent,
                unselectedColor = skin.colors.textMuted,
            ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = skin.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private fun messageFor(problem: FolderNameProblem): Int = when (problem) {
    FolderNameProblem.Blank -> R.string.folder_name_blank
    FolderNameProblem.TooLong -> R.string.folder_name_too_long
    FolderNameProblem.Duplicate -> R.string.folder_name_duplicate
}
