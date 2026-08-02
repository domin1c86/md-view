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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdview.MainViewModel
import com.mdview.Mode
import com.mdview.R
import com.mdview.ui.theme.LocalSkin
import com.mdview.ui.theme.ReadingTypography

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
    readingScale: Float,
    backEnabled: Boolean,
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
    // enabled handler registered last. [backEnabled] is what keeps that true while both
    // screens are briefly composed together during the transition.
    BackHandler(enabled = backEnabled) { viewModel.goToDashboard() }

    val skin = LocalSkin.current

    Scaffold(
        modifier = modifier,
        containerColor = skin.colors.canvas,
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> SkinSnackbar(data) } },
        topBar = {
            TopAppBar(
                title = {
                    val name = state.fileName ?: stringResource(R.string.untitled)
                    // The bullet stays part of the title's text rather than becoming a
                    // dot composable: it is the accessibility signal for "unsaved", and
                    // MdViewAppTest reads it as text. Only its colour is new.
                    Text(
                        text = buildAnnotatedString {
                            append(name)
                            if (state.isDirty) {
                                withStyle(SpanStyle(color = skin.colors.danger)) { append(" •") }
                            }
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
                    SkinDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
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
                    containerColor = skin.colors.canvas,
                    titleContentColor = skin.colors.textPrimary,
                    navigationIconContentColor = skin.colors.textSecondary,
                    actionIconContentColor = skin.colors.textSecondary,
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
            // Scoped to the document itself: the reading-size preference is about the
            // text being read, not the top bar, dialogs and navigation labels.
            ReadingTypography(readingScale) {
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
    }

    // `visible` rather than an `if`: SkinDialog has to outlive its own dismissal by the
    // length of the exit animation, so it owns that decision instead of the call site.
    SkinDialog(
        visible = pendingAction != null,
        onDismissRequest = { pendingAction = null },
        title = stringResource(R.string.unsaved_title),
        body = stringResource(R.string.unsaved_body),
        confirmLabel = stringResource(R.string.discard),
        onConfirm = {
            val action = pendingAction
            pendingAction = null
            when (action) {
                PendingAction.Open -> onOpenPicker()
                PendingAction.New -> viewModel.newDocument()
                null -> Unit
            }
        },
        dismissLabel = stringResource(R.string.cancel),
        // Discarding unsaved text is the one irreversible thing this dialog offers.
        destructive = true,
    )
}
