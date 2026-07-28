package com.mdview.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdview.Destination
import com.mdview.MainViewModel
import com.mdview.data.LibraryState
import com.mdview.ui.dashboard.DashboardScreen
import com.mdview.ui.dashboard.MineTab

/**
 * Picks between the app's two screens.
 *
 * Each screen composes its own `BackHandler`. `OnBackPressedDispatcher` dispatches to
 * the most recently registered enabled handler, so keeping them inside the screen that
 * owns them is what stops the dashboard's tab handling from swallowing the document's
 * unsaved-changes prompt.
 */
@Composable
fun MdViewRoot(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val library by viewModel.libraryState.collectAsStateWithLifecycle()
    val hasDraft by viewModel.hasUntitledDraft.collectAsStateWithLifecycle()

    // Many providers report .md as an unknown binary type, so accept that too --
    // filtering on text/* alone hides the very files this app exists to open.
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::open) }

    fun launchOpen() = openLauncher.launch(arrayOf("text/*", "application/octet-stream"))

    when (state.destination) {
        Destination.Dashboard -> {
            val entries = (library as? LibraryState.Content)?.entries.orEmpty()

            // Checking which grants survived is a Binder round trip, so it happens once
            // per visit to the dashboard rather than once per card.
            var reachable by remember { mutableStateOf(emptySet<String>()) }
            LaunchedEffect(entries.size) { reachable = viewModel.reachableUris() }

            DashboardScreen(
                tab = state.tab,
                entries = entries,
                reachable = reachable,
                hasDraft = hasDraft,
                onSelectTab = viewModel::showTab,
                onOpenPicker = ::launchOpen,
                onNewDocument = viewModel::newDocument,
                onOpenDraft = viewModel::openUntitledDraft,
                onOpen = { viewModel.open(it.uri.toUri()) },
                onToggleFavorite = { viewModel.setFavorite(it.uri, !it.isFavorite) },
                onForget = { viewModel.forget(it.uri) },
                settingsContent = { MineTab(it) },
                modifier = modifier,
            )
        }

        Destination.Document -> MdViewApp(
            viewModel = viewModel,
            onOpenPicker = ::launchOpen,
            modifier = modifier,
        )
    }
}
