package com.mdview.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdview.Destination
import com.mdview.MainViewModel
import com.mdview.MdViewApplication
import com.mdview.R
import com.mdview.data.FolderAccess
import com.mdview.data.FolderGrant
import com.mdview.data.InvalidSkinException
import com.mdview.data.LanguageChoice
import com.mdview.data.LibraryState
import com.mdview.data.RemoteImagePolicy
import com.mdview.data.Settings
import com.mdview.data.SkinImporter
import com.mdview.markdown.GrantedFolderImages
import com.mdview.markdown.LocalDocumentImages
import com.mdview.markdown.LocalRemoteImages
import com.mdview.markdown.RemoteImageAccess
import com.mdview.markdown.unmeteredNetwork
import com.mdview.ui.dashboard.DashboardScreen
import com.mdview.ui.dashboard.MineTab
import com.mdview.ui.theme.BuiltInSkins
import com.mdview.ui.theme.MdViewMotion
import com.mdview.ui.theme.Skin
import kotlinx.coroutines.launch

/**
 * Picks between the app's two screens.
 *
 * Each screen composes its own `BackHandler`. `OnBackPressedDispatcher` dispatches to
 * the most recently registered enabled handler, so keeping them inside the screen that
 * owns them is what stops the dashboard's tab handling from swallowing the document's.
 */
@Composable
fun MdViewRoot(
    viewModel: MainViewModel,
    settings: Settings,
    systemDark: Boolean,
    onChangeSettings: ((Settings) -> Settings) -> Unit,
    onChangeLanguage: (LanguageChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val library by viewModel.libraryState.collectAsStateWithLifecycle()
    val hasDraft by viewModel.hasUntitledDraft.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val skinStore = remember(context) { MdViewApplication.from(context).skins }
    val imported by skinStore.imported.collectAsStateWithLifecycle()
    val catalog = remember(imported) { BuiltInSkins.all + imported }

    // Held as a resource id rather than a resolved string: lint rejects
    // LocalContext.getString in composition, and a stored id follows a language change.
    var importError by remember { mutableStateOf<Int?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importError = null
        scope.launch {
            importError = runCatching {
                skinStore.import(SkinImporter(context.contentResolver).read(uri))
            }.fold(onSuccess = { null }, onFailure = ::messageFor)
        }
    }

    fun deleteSkin(skin: Skin) {
        scope.launch {
            skinStore.delete(skin.id)
            // A preference pointing at a skin that no longer exists still resolves to the
            // default, but leaving it dangling would make the picker show nothing selected.
            onChangeSettings { current ->
                current.copy(
                    lightSkinId = current.lightSkinId.takeIf { it != skin.id }
                        ?: BuiltInSkins.Paper.id,
                    darkSkinId = current.darkSkinId.takeIf { it != skin.id }
                        ?: BuiltInSkins.Ink.id,
                )
            }
        }
    }

    // Many providers report .md as an unknown binary type, so accept that too --
    // filtering on text/* alone hides the very files this app exists to open.
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::open) }

    fun launchOpen() = openLauncher.launch(arrayOf("text/*", "application/octet-stream"))

    val folderStore = remember(context) { MdViewApplication.from(context).folders }
    val folderAccess = remember(context) { FolderAccess(context.contentResolver) }
    val grants by folderStore.grants.collectAsStateWithLifecycle()

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { tree ->
        if (tree == null) return@rememberLauncherForActivityResult
        scope.launch {
            if (!folderAccess.persist(tree)) return@launch
            val evicted = folderStore.add(
                FolderGrant(
                    treeUri = tree.toString(),
                    displayName = folderAccess.displayName(tree),
                    grantedAt = System.currentTimeMillis(),
                )
            )
            // A grant the store can no longer list is one the user can never revoke.
            evicted.forEach { grant -> grant.treeUri.toUri().let(folderAccess::release) }
        }
    }

    fun forgetFolder(grant: FolderGrant) {
        scope.launch {
            // Release before forgetting: the store is the only record of what was taken,
            // so dropping the row first would strand the permission.
            folderAccess.release(grant.treeUri.toUri())
            folderStore.remove(grant.treeUri)
        }
    }

    // Keyed on the document *and* the grants, which is what makes a freshly granted
    // folder re-resolve every figure already on screen: a new grant list is a new key, a
    // new instance, and a new value for a static CompositionLocal.
    val documentImages = remember(state.uri, grants) {
        GrantedFolderImages(state.uri, grants) {
            // OpenDocumentTree forwards its input as DocumentsContract.EXTRA_INITIAL_URI
            // on API 26+, which is minSdk. A hint only -- DocumentsUI may ignore it.
            folderLauncher.launch(state.uri)
        }
    }

    CompositionLocalProvider(
        LocalRemoteImages provides rememberImageAccess(settings),
        LocalDocumentImages provides documentImages,
    ) {
        AnimatedContent(
            targetState = state.destination,
            transitionSpec = {
                val forward = targetState == Destination.Document
                MdViewMotion.screenEnter(forward) togetherWith MdViewMotion.screenExit(forward)
            },
            label = "destination",
        ) { destination ->
            // Both screens are composed at once while the transition runs, and each owns a
            // BackHandler. `OnBackPressedDispatcher` dispatches to the most recently
            // registered *enabled* handler, so without this gate a back press during those
            // ~220 ms would reach the screen that is on its way out. True only for the one
            // arriving.
            val backEnabled = transition.targetState == EnterExitState.Visible

            when (destination) {
                Destination.Dashboard -> {
                    val entries = (library as? LibraryState.Content)?.entries.orEmpty()

                    // Checking which grants survived is a Binder round trip, so it happens
                    // once per visit to the dashboard rather than once per card.
                    var reachable by remember { mutableStateOf(emptySet<String>()) }
                    LaunchedEffect(entries.size) { reachable = viewModel.reachableUris() }

                    DashboardScreen(
                        tab = state.tab,
                        entries = entries,
                        reachable = reachable,
                        hasDraft = hasDraft,
                        backEnabled = backEnabled,
                        onSelectTab = viewModel::showTab,
                        onOpenPicker = ::launchOpen,
                        onNewDocument = viewModel::newDocument,
                        onOpenDraft = viewModel::openUntitledDraft,
                        onOpen = { viewModel.open(it.uri.toUri()) },
                        onToggleFavorite = { viewModel.setFavorite(it.uri, !it.isFavorite) },
                        onForget = { viewModel.forget(it.uri) },
                        settingsContent = { contentModifier ->
                            MineTab(
                                settings = settings,
                                skins = catalog,
                                systemDark = systemDark,
                                onChange = onChangeSettings,
                                onChangeLanguage = onChangeLanguage,
                                onImportSkin = {
                                    // Providers routinely mislabel .json, so the filter is
                                    // wide and the codec does the actual rejecting.
                                    importLauncher.launch(
                                        arrayOf(
                                            "application/json",
                                            "text/plain",
                                            "application/octet-stream",
                                        )
                                    )
                                },
                                onDeleteSkin = ::deleteSkin,
                                importError = importError,
                                folders = grants,
                                onForgetFolder = ::forgetFolder,
                                modifier = contentModifier,
                            )
                        },
                        modifier = modifier,
                    )
                }

                Destination.Document -> MdViewApp(
                    viewModel = viewModel,
                    onOpenPicker = ::launchOpen,
                    readingScale = settings.readingSize.scale,
                    backEnabled = backEnabled,
                    modifier = modifier,
                )
            }
        }
    }
}

/**
 * Resolves the image preference against the network the device is actually on.
 *
 * Watched rather than read once: a one-shot check would leave images blocked for the
 * rest of the session after the user joins Wi-Fi.
 */
@Composable
private fun rememberImageAccess(settings: Settings): RemoteImageAccess {
    val context = LocalContext.current
    val unmetered by produceState(initialValue = false, context, settings.remoteImages) {
        if (settings.remoteImages == RemoteImagePolicy.Unmetered) {
            context.unmeteredNetwork().collect { value = it }
        }
    }

    return when (settings.remoteImages) {
        RemoteImagePolicy.Never -> RemoteImageAccess.Blocked
        RemoteImagePolicy.Always -> RemoteImageAccess.Allowed
        RemoteImagePolicy.Unmetered ->
            if (unmetered) RemoteImageAccess.Allowed else RemoteImageAccess.Blocked
    }
}

/** Turns a failed import into something a person can act on. */
private fun messageFor(error: Throwable): Int = when ((error as? InvalidSkinException)?.reason) {
    InvalidSkinException.Reason.TooLarge -> R.string.skin_error_too_large
    InvalidSkinException.Reason.BadId -> R.string.skin_error_id
    InvalidSkinException.Reason.BadName -> R.string.skin_error_name
    InvalidSkinException.Reason.UnknownBase -> R.string.skin_error_base
    InvalidSkinException.Reason.ReservedId -> R.string.skin_error_reserved
    InvalidSkinException.Reason.TooMany -> R.string.skin_error_full
    else -> R.string.skin_error_format
}
