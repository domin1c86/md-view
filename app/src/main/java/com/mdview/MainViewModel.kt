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
import com.mdview.data.LoadedDocument
import com.mdview.data.UnreadableDocumentException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException

enum class Mode { Preview, Edit }

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
    val uri: Uri? = null,
    val fileName: String? = null,
    val mode: Mode = Mode.Preview,
    val isDirty: Boolean = false,
    val isBusy: Boolean = false,
    val message: UserMessage? = null,
)

class MainViewModel(
    private val documents: DocumentSource,
    private val drafts: DraftStore,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    /** The single source of truth for the document text, shared by both modes. */
    val textState = TextFieldState()

    private var savedText: String = ""

    /** How the open file was encoded, so saving puts the same bytes back. */
    private var format = LoadedDocument("")

    /** Set as soon as a load starts, so a repeat request can be recognised before
     *  the read finishes and reaches [uiState]. */
    private var lastRequestedUri: Uri? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

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

        val restored = savedState.get<String>(KEY_URI)?.toUri()
        if (restored != null) {
            open(restored)
        } else {
            // Nothing was open, but there may still be an unsaved scratch document.
            viewModelScope.launch { restoreUntitledDraft() }
        }
    }

    /** Opens [uri] unless it is already the document on screen. */
    fun openFromIntent(uri: Uri) {
        if (lastRequestedUri != uri) open(uri)
    }

    fun open(uri: Uri) {
        lastRequestedUri = uri
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            documents.persistAccess(uri)
            runCatching { documents.read(uri) }
                .onSuccess { document ->
                    format = document
                    savedText = document.text
                    savedState[KEY_URI] = uri.toString()

                    // A draft that differs from what is on disk is work the user did
                    // and never saved. Prefer it, and say so.
                    val draft = drafts.load(DraftStore.keyFor(uri.toString()))
                    val recovered = draft != null && draft != document.text
                    setText(if (recovered) draft else document.text)

                    _uiState.update {
                        it.copy(
                            uri = uri,
                            fileName = documents.displayName(uri),
                            mode = Mode.Preview,
                            isDirty = recovered,
                            isBusy = false,
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
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isBusy = false, message = UserMessage(error.openFailureText()))
                    }
                }
        }
    }

    /** Starts an empty document, dropping whatever was open. */
    fun newDocument() {
        viewModelScope.launch {
            drafts.clear(draftKey)
            lastRequestedUri = null
            format = LoadedDocument("")
            savedText = ""
            savedState.remove<String>(KEY_URI)
            setText("")
            _uiState.update {
                it.copy(uri = null, fileName = null, mode = Mode.Edit, isDirty = false, isBusy = false)
            }
        }
    }

    /** Writes back to the currently open document. No-op when nothing is open. */
    fun save() {
        val uri = _uiState.value.uri ?: return
        writeTo(uri)
    }

    /** Writes to a newly created document and adopts it as the open one. */
    fun saveAs(uri: Uri) {
        documents.persistAccess(uri)
        savedState[KEY_URI] = uri.toString()
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
                    _uiState.update {
                        it.copy(
                            uri = uri,
                            fileName = if (adopt) documents.displayName(uri) else it.fileName,
                            isDirty = false,
                            isBusy = false,
                            message = UserMessage(R.string.saved),
                        )
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
        viewModelScope.launch { persistDraft(textState.text.toString()) }
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
        if (current == savedText) drafts.clear(draftKey) else drafts.save(draftKey, current)
    }

    private suspend fun restoreUntitledDraft() {
        val draft = drafts.load(DraftStore.UNTITLED_KEY)?.takeIf { it.isNotEmpty() } ?: return
        setText(draft)
        _uiState.update {
            it.copy(
                mode = Mode.Edit,
                isDirty = true,
                message = UserMessage(
                    resId = R.string.draft_restored,
                    actionResId = R.string.discard,
                    action = MessageAction.DiscardRestoredDraft,
                ),
            )
        }
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

        /** Long enough not to write on every keystroke, short enough to survive a kill. */
        private const val AUTOSAVE_DELAY_MS = 500L

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = context.applicationContext
                MainViewModel(
                    documents = DocumentRepository(app.contentResolver),
                    drafts = DraftStore(File(app.filesDir, "drafts")),
                    savedState = createSavedStateHandle(),
                )
            }
        }
    }
}
