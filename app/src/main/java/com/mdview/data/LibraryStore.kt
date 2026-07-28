package com.mdview.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The list of documents the user has opened, and which of them they starred.
 *
 * Shaped like [DraftStore] — a plain [File] rather than a Context so it can be tested
 * against a temp directory, an injectable [dispatcher] so tests can observe writes, and
 * an atomic temp-file-plus-rename so a process death midway leaves the previous list
 * intact.
 *
 * Unlike [DraftStore] this is a *single* index file rewritten in full, so concurrent
 * writers would clobber each other: starring a document while an open() bumps its
 * timestamp would lose one of the two, and both would be using the same scratch file.
 * Every mutation is therefore serialised behind [mutex], and the in-memory [state] is
 * the source of truth once loaded.
 */
class LibraryStore(
    private val directory: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val mutex = Mutex()
    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    /** Reads the file into memory. Safe to call more than once. */
    suspend fun load() {
        mutex.withLock { publish(readFromDisk()) }
    }

    /**
     * Records that [entry]'s document was opened, moving it to the top of the list.
     *
     * An existing row keeps its [LibraryEntry.isFavorite] — opening a document is not a
     * statement about whether it is starred.
     */
    suspend fun record(entry: LibraryEntry) = mutate { entries ->
        val existing = entries.firstOrNull { it.uri == entry.uri }
        val merged = entry.copy(isFavorite = existing?.isFavorite ?: entry.isFavorite)
        listOf(merged) + entries.filterNot { it.uri == entry.uri }
    }

    /**
     * Refreshes the heading and excerpt for [uri] without disturbing its position. Used
     * after a save, when the text changed but the document was not re-opened.
     */
    suspend fun updateSummary(uri: String, title: String?, excerpt: String) = mutate { entries ->
        entries.map { if (it.uri == uri) it.copy(title = title, excerpt = excerpt) else it }
    }

    suspend fun setFavorite(uri: String, favorite: Boolean) = mutate { entries ->
        entries.map { if (it.uri == uri) it.copy(isFavorite = favorite) else it }
    }

    /** Drops [uri] and returns the row that went, so the caller can release its grant. */
    suspend fun remove(uri: String): LibraryEntry? {
        var removed: LibraryEntry? = null
        mutate { entries ->
            removed = entries.firstOrNull { it.uri == uri }
            entries.filterNot { it.uri == uri }
        }
        return removed
    }

    /** Everything currently known, newest first. Empty until [load] has run. */
    fun snapshot(): List<LibraryEntry> = (_state.value as? LibraryState.Content)?.entries.orEmpty()

    private suspend fun mutate(transform: (List<LibraryEntry>) -> List<LibraryEntry>) {
        mutex.withLock {
            // A mutation can land before load() finishes -- the ViewModel opens a
            // document as soon as an ACTION_VIEW intent arrives. Starting from an empty
            // list there would rewrite the file with one row and drop the rest.
            val current = if (_state.value is LibraryState.Loading) readFromDisk() else snapshot()
            val next = evict(transform(current).sortedByDescending { it.lastOpened })
            publish(next)
            write(next)
        }
    }

    private suspend fun readFromDisk(): List<LibraryEntry> = withContext(dispatcher) {
        runCatching { file.takeIf { it.isFile }?.readText() }
            .getOrNull()
            ?.let(LibraryCodec::decode)
            .orEmpty()
            .sortedByDescending { it.lastOpened }
    }

    private suspend fun write(entries: List<LibraryEntry>) = withContext(dispatcher) {
        runCatching {
            directory.mkdirs()
            val encoded = LibraryCodec.encode(entries)
            val scratch = File(directory, "$FILE_NAME.tmp")
            scratch.writeText(encoded)
            if (!scratch.renameTo(file)) {
                file.writeText(encoded)
                scratch.delete()
            }
        }
    }

    /**
     * Caps the list, but never at a favourite's expense. Starring a document is the user
     * saying they want it kept; dropping it because they have not opened it lately would
     * throw away the one row they cared about most.
     */
    private fun evict(entries: List<LibraryEntry>): List<LibraryEntry> {
        if (entries.size <= MAX_ENTRIES) return entries
        val favorites = entries.filter { it.isFavorite }
        val rest = entries.filterNot { it.isFavorite }
        val room = (MAX_ENTRIES - favorites.size).coerceAtLeast(0)
        return (favorites + rest.take(room)).sortedByDescending { it.lastOpened }
    }

    private fun publish(entries: List<LibraryEntry>) {
        _state.value = if (entries.isEmpty()) LibraryState.Empty else LibraryState.Content(entries)
    }

    private val file: File get() = File(directory, FILE_NAME)

    companion object {
        private const val FILE_NAME = "entries.tsv"

        /**
         * Android caps how many URI grants an app may persist, so an unbounded list
         * would eventually stop being able to reopen anything.
         */
        const val MAX_ENTRIES = 50
    }
}
