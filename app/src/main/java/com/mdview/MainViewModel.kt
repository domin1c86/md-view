package com.mdview

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mdview.data.DocumentRepository
import com.mdview.data.DocumentSource
import com.mdview.data.DraftStore
import com.mdview.data.LibraryEntry
import com.mdview.data.LibraryState
import com.mdview.data.LibraryStore
import com.mdview.data.LoadedDocument
import com.mdview.data.PersistedAccess
import com.mdview.data.UnreadableDocumentException
import com.mdview.markdown.DocumentSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

enum class Mode { Preview, Edit }

/** The app's two top-level screens. */
enum class Destination { Dashboard, Document }

/** The dashboard's bottom-navigation tabs. */
enum class DashboardTab { Recent, Favorites, Mine }

/** Something the user can do about a message, offered as a snackbar button. */
enum class MessageAction { DiscardRestoredDraft }

/** A one-shot message for the snackbar; [formatArg] fills the string resource. */
data class UserMessage(
    @param:StringRes val resId: Int,
    val formatArg: String? = null,
    @param:StringRes val actionResId: Int? = null,
    val action: MessageAction? = null,
    val id: Long = nextId(),
)

private var messageCounter = 0L
private fun nextId(): Long = ++messageCounter

data class UiState(
    val destination: Destination = Destination.Dashboard,
    val tab: DashboardTab = DashboardTab.Recent,
    val uri: Uri? = null,
    val fileName: String? = null,
    val mode: Mode = Mode.Preview,
    val isDirty: Boolean = false,
    val isBusy: Boolean = false,
    /** False when the document was opened read-only, so Save is refused up front. */
    val canWrite: Boolean = true,
    val message: UserMessage? = null,
)

class MainViewModel(
    private val documents: DocumentSource,
    private val drafts: DraftStore,
    private val library: LibraryStore,
    private val savedState: SavedStateHandle,
    /**
     * Where Markdown gets parsed for a card's excerpt. Injectable because
     * `Dispatchers.Default` is not driven by the test scheduler, so work sent there
     * outlives `advanceUntilIdle` and the assertions read stale state.
     */
    private val parsing: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    /** The single source of truth for the document text, shared by both modes. */
    val textState = TextFieldState()

    private var savedText: String = ""

    /** How the open file was encoded, so saving puts the same bytes back. */
    private var format = LoadedDocument("")

    /** Set as soon as a load starts, so a repeat request can be recognised before
     *  the read finishes and reaches [uiState]. */
    private var lastRequestedUri: Uri? = null

    /**
     * Whether a document has actually been opened in this session.
     *
     * Guards the autosave. `snapshotFlow` emits its current value the moment it is
     * collected, so without this the debounce fires half a second after launch with an
     * empty buffer, sees it match the equally-empty `savedText`, and deletes the
     * untitled draft -- destroying unsaved work belonging to the *previous* session
     * while the user is still looking at the dashboard.
     */
    private var hasAdoptedDocument = false

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Whether there is scratch work with no file behind it, shown as its own card. */
    private val _hasUntitledDraft = MutableStateFlow(false)
    val hasUntitledDraft: StateFlow<Boolean> = _hasUntitledDraft.asStateFlow()

    val libraryState: StateFlow<LibraryState> = library.state

    private val draftKey: String
        get() = DraftStore.keyFor(_uiState.value.uri?.toString())

    private val textChanges = snapshotFlow { textState.text.toString() }

    init {
        viewModelScope.launch {
            textChanges.collect { current ->
                _uiState.update { it.copy(isDirty = current != savedText) }
            }
        }

        viewModelScope.launch { autosaveDrafts() }
        viewModelScope.launch { refreshUntitledDraft() }

        // Three ways to come back, not two. Process death on the document screen has to
        // restore whichever of the two kinds of document was open, and a cold launch has
        // to load nothing at all -- the dashboard is not a document.
        val wasOnDocument = savedState.get<String>(KEY_SCREEN) == Destination.Document.name
        val restored = savedState.get<String>(KEY_URI)?.toUri()
        when {
            !wasOnDocument -> Unit
            restored != null -> open(restored)
            else -> viewModelScope.launch { restoreUntitledDraft() }
        }
    }

    /**
     * Opens [uri] handed over by another app.
     *
     * The dedupe guards the *reload* only. A rotation re-delivers the same intent to a
     * fresh Activity and re-reading would discard unsaved edits -- but navigation still
     * has to happen every time, or tapping the same file again after backing out to the
     * dashboard would appear to do nothing.
     */
    fun openFromIntent(uri: Uri) {
        if (lastRequestedUri == uri) navigate(Destination.Document) else open(uri)
    }

    fun open(uri: Uri) {
        lastRequestedUri = uri
        hasAdoptedDocument = true
        // Written before the read rather than after: a process death partway through
        // would otherwise restore onto the document screen with nothing to show.
        savedState[KEY_URI] = uri.toString()
        navigate(Destination.Document)

        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val access = documents.persistAccess(uri)
            runCatching { documents.read(uri) }
                .onSuccess { document ->
                    format = document
                    savedText = document.text

                    // A draft that differs from what is on disk is work the user did
                    // and never saved. Prefer it, and say so.
                    val draft = drafts.load(DraftStore.keyFor(uri.toString()))
                    val recovered = draft != null && draft != document.text
                    val text = if (recovered) draft else document.text
                    setText(text)

                    val name = documents.displayName(uri)
                    _uiState.update {
                        it.copy(
                            uri = uri,
                            fileName = name,
                            mode = Mode.Preview,
                            isDirty = recovered,
                            isBusy = false,
                            canWrite = access != PersistedAccess.ReadOnly,
                            message = if (recovered) {
                                UserMessage(
                                    resId = R.string.draft_restored,
                                    actionResId = R.string.discard,
                                    action = MessageAction.DiscardRestoredDraft,
                                )
                            } else {
                                it.message
                            },
                        )
                    }
                    recordInLibrary(uri, name, text, access)
                }
                .onFailure { error ->
                    savedState.remove<String>(KEY_URI)
                    _uiState.update {
                        it.copy(
                            destination = Destination.Dashboard,
                            isBusy = false,
                            message = UserMessage(error.openFailureText()),
                        )
                    }
                    savedState[KEY_SCREEN] = Destination.Dashboard.name
                }
        }
    }

    private suspend fun recordInLibrary(
        uri: Uri,
        name: String?,
        text: String,
        access: PersistedAccess,
    ) {
        val summary = withContext(parsing) { DocumentSummary.of(text) }
        library.record(
            LibraryEntry(
                uri = uri.toString(),
                displayName = name ?: uri.lastPathSegment.orEmpty(),
                title = summary.title,
                excerpt = summary.excerpt,
                lastOpened = System.currentTimeMillis(),
                canWrite = access != PersistedAccess.ReadOnly,
                isTransient = access == PersistedAccess.None,
            )
        )
    }

    /** Starts an empty document, dropping whatever was open. */
    fun newDocument() {
        viewModelScope.launch {
            drafts.clear(draftKey)
            drafts.clear(DraftStore.UNTITLED_KEY)
            lastRequestedUri = null
            hasAdoptedDocument = true
            format = LoadedDocument("")
            savedText = ""
            savedState.remove<String>(KEY_URI)
            setText("")
            _uiState.update {
                it.copy(
                    destination = Destination.Document,
                    uri = null,
                    fileName = null,
                    mode = Mode.Edit,
                    isDirty = false,
                    isBusy = false,
                    canWrite = true,
                )
            }
            savedState[KEY_SCREEN] = Destination.Document.name
            refreshUntitledDraft()
        }
    }

    /** Picks up the scratch document the dashboard offers when one is waiting. */
    fun openUntitledDraft() {
        viewModelScope.launch {
            hasAdoptedDocument = true
            lastRequestedUri = null
            format = LoadedDocument("")
            savedText = ""
            savedState.remove<String>(KEY_URI)
            _uiState.update {
                it.copy(uri = null, fileName = null, isBusy = false, canWrite = true)
            }
            restoreUntitledDraft()
        }
    }

    fun showTab(tab: DashboardTab) = _uiState.update { it.copy(tab = tab) }

    /**
     * Leaves the document screen. The draft is written on the way out rather than left
     * to the debounce, because the dashboard is where the user goes before closing the
     * app.
     */
    fun goToDashboard() {
        viewModelScope.launch {
            persistDraft(textState.text.toString())
            refreshUntitledDraft()
            navigate(Destination.Dashboard)
        }
    }

    fun setFavorite(uri: String, favorite: Boolean) {
        viewModelScope.launch { library.setFavorite(uri, favorite) }
    }

    /** Forgets a document, handing back the URI grant that was taken for it. */
    fun forget(uri: String) {
        viewModelScope.launch {
            val removed = library.remove(uri) ?: return@launch
            drafts.clear(DraftStore.keyFor(uri))
            val access = when {
                removed.isTransient -> PersistedAccess.None
                removed.canWrite -> PersistedAccess.ReadWrite
                else -> PersistedAccess.ReadOnly
            }
            documents.releaseAccess(uri.toUri(), access)
        }
    }

    /** Which library entries can still be reached. Cheap to call, but it is a Binder trip. */
    suspend fun reachableUris(): Set<String> =
        withContext(Dispatchers.IO) { documents.persistedUris() }

    private fun navigate(destination: Destination) {
        _uiState.update { it.copy(destination = destination) }
        savedState[KEY_SCREEN] = destination.name
    }

    /** Writes back to the currently open document. No-op when nothing is open. */
    fun save() {
        val uri = _uiState.value.uri ?: return
        writeTo(uri)
    }

    /** Writes to a newly created document and adopts it as the open one. */
    fun saveAs(uri: Uri) {
        val access = documents.persistAccess(uri)
        hasAdoptedDocument = true
        savedState[KEY_URI] = uri.toString()
        _uiState.update { it.copy(canWrite = access != PersistedAccess.ReadOnly) }
        writeTo(uri, adopt = true)
    }

    private fun writeTo(uri: Uri, adopt: Boolean = false) {
        viewModelScope.launch {
            val content = textState.text.toString()
            val previousKey = draftKey
            _uiState.update { it.copy(isBusy = true) }
            runCatching { documents.write(uri, format.copy(text = content)) }
                .onSuccess {
                    savedText = content
                    // The text is on disk now, so the draft has nothing left to protect.
                    drafts.clear(previousKey)
                    if (adopt) drafts.clear(DraftStore.keyFor(uri.toString()))
                    val name = if (adopt) documents.displayName(uri) else _uiState.value.fileName
                    _uiState.update {
                        it.copy(
                            uri = uri,
                            fileName = name,
                            isDirty = false,
                            isBusy = false,
                            message = UserMessage(R.string.saved),
                        )
                    }
                    refreshUntitledDraft()

                    // The card's excerpt came from the text as it was opened, so a save
                    // is the moment it goes stale.
                    val summary = withContext(parsing) { DocumentSummary.of(content) }
                    if (adopt) {
                        recordInLibrary(uri, name, content, documents.persistAccess(uri))
                    } else {
                        library.updateSummary(uri.toString(), summary.title, summary.excerpt)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isBusy = false, message = UserMessage(error.saveFailureText()))
                    }
                }
        }
    }

    fun setMode(mode: Mode) = _uiState.update { it.copy(mode = mode) }

    fun toggleMode() = _uiState.update {
        it.copy(mode = if (it.mode == Mode.Preview) Mode.Edit else Mode.Preview)
    }

    fun consumeMessage(id: Long) = _uiState.update {
        if (it.message?.id == id) it.copy(message = null) else it
    }

    /** Runs the button a message offered, if it is still the current one. */
    fun onMessageAction(id: Long) {
        val message = _uiState.value.message?.takeIf { it.id == id } ?: return
        when (message.action) {
            MessageAction.DiscardRestoredDraft -> viewModelScope.launch {
                drafts.clear(draftKey)
                setText(savedText)
                _uiState.update { it.copy(isDirty = false) }
                refreshUntitledDraft()
            }

            null -> Unit
        }
        consumeMessage(id)
    }

    /**
     * Writes the draft now rather than waiting out the debounce. Called as the app
     * goes to the background, which is the moment before it may be killed.
     */
    fun flushDraft() {
        viewModelScope.launch {
            persistDraft(textState.text.toString())
            refreshUntitledDraft()
        }
    }

    /**
     * The dirty flag only guards the back gesture, and Android kills backgrounded
     * processes without one, so unsaved text needs somewhere to live that is not this
     * process's memory.
     */
    @OptIn(FlowPreview::class)
    private suspend fun autosaveDrafts() {
        textChanges.debounce(AUTOSAVE_DELAY_MS).collect { current -> persistDraft(current) }
    }

    private suspend fun persistDraft(current: String) {
        // Sitting on the dashboard must not touch the drafts of a previous session.
        if (!hasAdoptedDocument) return
        if (current == savedText) drafts.clear(draftKey) else drafts.save(draftKey, current)
    }

    private suspend fun refreshUntitledDraft() {
        _hasUntitledDraft.value = !drafts.load(DraftStore.UNTITLED_KEY).isNullOrEmpty()
    }

    /**
     * Loads the scratch document, or falls back to the dashboard when there is nothing
     * to load -- which happens if the process died between navigating to the editor and
     * typing anything.
     */
    private suspend fun restoreUntitledDraft() {
        val draft = drafts.load(DraftStore.UNTITLED_KEY)?.takeIf { it.isNotEmpty() }
        if (draft == null) {
            navigate(Destination.Dashboard)
            return
        }
        hasAdoptedDocument = true
        setText(draft)
        _uiState.update {
            it.copy(
                destination = Destination.Document,
                mode = Mode.Edit,
                isDirty = true,
                message = UserMessage(
                    resId = R.string.draft_restored,
                    actionResId = R.string.discard,
                    action = MessageAction.DiscardRestoredDraft,
                ),
            )
        }
        savedState[KEY_SCREEN] = Destination.Document.name
    }

    /**
     * Replaces the buffer and forgets the edit history along with it -- otherwise undo
     * walks backwards out of the document the user just opened and into the last one.
     */
    @OptIn(ExperimentalFoundationApi::class)
    private fun setText(text: String) {
        textState.setTextAndPlaceCursorAtEnd(text)
        textState.undoState.clearHistory()
    }

    @StringRes
    private fun Throwable.openFailureText(): Int = when {
        this is UnreadableDocumentException &&
            reason == UnreadableDocumentException.Reason.TooLarge -> R.string.error_open_too_large

        this is UnreadableDocumentException &&
            reason == UnreadableDocumentException.Reason.NotText -> R.string.error_open_not_text

        this is FileNotFoundException -> R.string.error_open_missing
        this is SecurityException -> R.string.error_open_denied
        else -> R.string.error_open_generic
    }

    @StringRes
    private fun Throwable.saveFailureText(): Int = when (this) {
        is FileNotFoundException -> R.string.error_save_missing
        is SecurityException -> R.string.error_save_denied
        else -> R.string.error_save_generic
    }

    companion object {
        private const val KEY_URI = "open_document_uri"
        private const val KEY_SCREEN = "destination"

        /** Long enough not to write on every keystroke, short enough to survive a kill. */
        private const val AUTOSAVE_DELAY_MS = 500L

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = MdViewApplication.from(context)
                MainViewModel(
                    documents = DocumentRepository(app.contentResolver),
                    drafts = DraftStore(File(app.filesDir, "drafts")),
                    library = app.library,
                    savedState = createSavedStateHandle(),
                )
            }
        }
    }
}
