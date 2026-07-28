package com.mdview.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdview.MainViewModel
import com.mdview.Mode
import com.mdview.R

/**
 * What the user asked for while the document still had unsaved changes.
 *
 * Only actions that *replace* the buffer need confirming. Leaving for the dashboard does
 * not: the draft is written on the way out and the document comes back with its unsaved
 * text intact, so there is nothing to warn about.
 */
private enum class PendingAction { Open, New }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MdViewApp(
    viewModel: MainViewModel,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    // Hoisted out of the screens themselves: each mode switch tears its screen out of
    // composition, so state remembered down there would put the reader back at the top
    // of the document every time. Both of these already save across rotation.
    val previewScrollState = rememberLazyListState()
    val editorScrollState = rememberScrollState()

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri -> uri?.let(viewModel::saveAs) }

    fun requestOpen() {
        if (state.isDirty) pendingAction = PendingAction.Open else onOpenPicker()
    }

    fun requestNew() {
        if (state.isDirty) pendingAction = PendingAction.New else viewModel.newDocument()
    }


    state.message?.let { message ->
        // Resolved during composition rather than inside the effect, so the string
        // follows the current configuration.
        val text = if (message.formatArg != null) {
            stringResource(message.resId, message.formatArg)
        } else {
            stringResource(message.resId)
        }
        val actionLabel = message.actionResId?.let { stringResource(it) }
        LaunchedEffect(message.id) {
            val result = snackbarHostState.showSnackbar(
                message = text,
                actionLabel = actionLabel,
                duration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.onMessageAction(message.id)
            } else {
                viewModel.consumeMessage(message.id)
            }
        }
    }

    // The document screen's only back handler, composed here rather than at the root so
    // it cannot fight with the dashboard's tab handling -- the dispatcher runs whichever
    // enabled handler registered last.
    BackHandler { viewModel.goToDashboard() }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = buildString {
                            append(state.fileName ?: stringResource(R.string.untitled))
                            if (state.isDirty) append(" •")
                        },
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = viewModel::goToDashboard) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back_to_list),
                        )
                    }
                },
                actions = {
                    val editing = state.mode == Mode.Edit
                    if (editing) {
                        val undoState = viewModel.textState.undoState
                        IconButton(onClick = { undoState.undo() }, enabled = undoState.canUndo) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Undo,
                                contentDescription = stringResource(R.string.undo),
                            )
                        }
                        IconButton(onClick = { undoState.redo() }, enabled = undoState.canRedo) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Redo,
                                contentDescription = stringResource(R.string.redo),
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.toggleMode() }) {
                        Icon(
                            imageVector = if (editing) Icons.Outlined.Visibility else Icons.Outlined.Edit,
                            contentDescription = stringResource(
                                if (editing) R.string.show_preview else R.string.show_source
                            ),
                        )
                    }
                    IconButton(
                        onClick = { viewModel.save() },
                        // A read-only grant means the write will be refused, so the
                        // button says so now rather than after ten minutes of typing.
                        enabled = state.isDirty && state.uri != null && state.canWrite,
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = stringResource(R.string.save))
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_actions))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.open_file)) },
                            onClick = {
                                menuExpanded = false
                                requestOpen()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_document)) },
                            onClick = {
                                menuExpanded = false
                                requestNew()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.save_as)) },
                            onClick = {
                                menuExpanded = false
                                createLauncher.launch(state.fileName ?: "document.md")
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            if (state.isBusy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            when {
                state.mode == Mode.Edit ->
                    EditorScreen(viewModel.textState, editorScrollState, Modifier.weight(1f))

                state.uri == null && viewModel.textState.text.isEmpty() ->
                    EmptyState(
                        onOpen = ::requestOpen,
                        onNew = ::requestNew,
                        modifier = Modifier.weight(1f),
                    )

                else -> PreviewScreen(
                    source = viewModel.textState.text.toString(),
                    scrollState = previewScrollState,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (pendingAction != null) {
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(stringResource(R.string.unsaved_title)) },
            text = { Text(stringResource(R.string.unsaved_body)) },
            confirmButton = {
                TextButton(onClick = {
                    val action = pendingAction
                    pendingAction = null
                    when (action) {
                        PendingAction.Open -> onOpenPicker()
                        PendingAction.New -> viewModel.newDocument()
                        null -> Unit
                    }
                }) { Text(stringResource(R.string.discard)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
