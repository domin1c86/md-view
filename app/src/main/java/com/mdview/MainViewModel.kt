package com.mdview

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mdview.data.DocumentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Mode { Preview, Edit }

/** A one-shot message for the snackbar; [formatArg] fills the string resource. */
data class UserMessage(val resId: Int, val formatArg: String? = null, val id: Long = nextId())

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
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val repository = DocumentRepository(application.contentResolver)

    /** The single source of truth for the document text, shared by both modes. */
    val textState = TextFieldState()

    private var savedText: String = ""

    /** Set as soon as a load starts, so a repeat request can be recognised before
     *  the read finishes and reaches [uiState]. */
    private var lastRequestedUri: Uri? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            snapshotFlow { textState.text.toString() }.collect { current ->
                _uiState.update { it.copy(isDirty = current != savedText) }
            }
        }

        // Reload whatever was open before the process was killed. Unsaved edits do
        // not survive that -- keeping a whole document in the saved-state bundle
        // risks TransactionTooLargeException.
        savedState.get<String>(KEY_URI)?.let { open(it.toUri()) }
    }

    /** Opens [uri] unless it is already the document on screen. */
    fun openFromIntent(uri: Uri) {
        if (lastRequestedUri != uri) open(uri)
    }

    fun open(uri: Uri) {
        lastRequestedUri = uri
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            repository.persistAccess(uri)
            runCatching { repository.read(uri) }
                .onSuccess { content ->
                    savedText = content
                    textState.setTextAndPlaceCursorAtEnd(content)
                    savedState[KEY_URI] = uri.toString()
                    _uiState.update {
                        it.copy(
                            uri = uri,
                            fileName = repository.displayName(uri),
                            mode = Mode.Preview,
                            isDirty = false,
                            isBusy = false,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isBusy = false, message = UserMessage(R.string.error_open, error.readableMessage()))
                    }
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
        repository.persistAccess(uri)
        savedState[KEY_URI] = uri.toString()
        writeTo(uri, adopt = true)
    }

    private fun writeTo(uri: Uri, adopt: Boolean = false) {
        viewModelScope.launch {
            val content = textState.text.toString()
            _uiState.update { it.copy(isBusy = true) }
            runCatching { repository.write(uri, content) }
                .onSuccess {
                    savedText = content
                    _uiState.update {
                        it.copy(
                            uri = uri,
                            fileName = if (adopt) repository.displayName(uri) else it.fileName,
                            isDirty = false,
                            isBusy = false,
                            message = UserMessage(R.string.saved),
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isBusy = false, message = UserMessage(R.string.error_save, error.readableMessage()))
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

    private fun Throwable.readableMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: this::class.java.simpleName

    private companion object {
        const val KEY_URI = "open_document_uri"
    }
}
