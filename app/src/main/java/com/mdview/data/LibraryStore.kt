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
import java.util.UUID

/**
 * The list of documents the user has opened, which of them they starred, and the folders
 * they filed them into.
 *
 * Shaped like [DraftStore] — a plain [File] rather than a Context so it can be tested
 * against a temp directory, an injectable [dispatcher] so tests can observe writes, and
 * an atomic temp-file-plus-rename so a process death midway leaves the previous list
 * intact.
 *
 * Unlike [DraftStore] these are *index* files rewritten in full, so concurrent writers
 * would clobber each other: starring a document while an open() bumps its timestamp would
 * lose one of the two, and both would be using the same scratch file. Every mutation is
 * therefore serialised behind [mutex], and the in-memory state is the source of truth once
 * loaded.
 *
 * **Folders live here rather than in a store of their own**, even though they are a
 * separate file. Deleting a folder has to unfile its documents, which is two files that
 * must move together or not at all; eviction has to know which rows are filed; and a fifth
 * process-wide store is a fifth thing to remember in [MdViewApplication], the test wipe and
 * the reset. One lock over both files is the cheaper answer to all three.
 *
 * Nothing here creates a directory. A [LibraryFolder] is a label in this index and the
 * documents never move on disk; see that class for why the app could not do otherwise.
 */
class LibraryStore(
    private val directory: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val mutex = Mutex()
    private var loaded = false

    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    /** Oldest first, so the chips on the dashboard keep a stable order as more are added. */
    private val _folders = MutableStateFlow<List<LibraryFolder>>(emptyList())
    val folders: StateFlow<List<LibraryFolder>> = _folders.asStateFlow()

    /**
     * Reads both files into memory. Safe to call more than once.
     *
     * The result goes through [tidy] rather than straight to [publish], because the two
     * files can disagree: a process death between writing `entries.tsv` and `folders.tsv`
     * leaves documents pointing at a folder that never arrived, and they must come back
     * unfiled rather than invisible. Nothing is rewritten here — the next mutation does
     * that, and a read that fixes itself on disk would be a surprising thing for `load`
     * to do.
     */
    suspend fun load() {
        mutex.withLock { publish(tidy(readFromDisk())) }
    }

    /**
     * Records that [entry]'s document was opened, moving it to the top of the list.
     *
     * An existing row keeps its [LibraryEntry.isFavorite] and its [LibraryEntry.folderId]
     * — opening a document is not a statement about whether it is starred, nor about where
     * it is filed. Filing on import is a separate [setFolder] call, which is what lets it
     * *move* a document that was already in another folder.
     */
    suspend fun record(entry: LibraryEntry) = mutate { library ->
        val existing = library.entries.firstOrNull { it.uri == entry.uri }
        val merged = entry.copy(
            isFavorite = existing?.isFavorite ?: entry.isFavorite,
            folderId = existing?.folderId ?: entry.folderId,
        )
        library.withEntries(listOf(merged) + library.entries.filterNot { it.uri == entry.uri })
    }

    /**
     * Refreshes the heading and excerpt for [uri] without disturbing its position. Used
     * after a save, when the text changed but the document was not re-opened.
     */
    suspend fun updateSummary(uri: String, title: String?, excerpt: String) = mutate { library ->
        library.mapEntries { if (it.uri == uri) it.copy(title = title, excerpt = excerpt) else it }
    }

    suspend fun setFavorite(uri: String, favorite: Boolean) = mutate { library ->
        library.mapEntries { if (it.uri == uri) it.copy(isFavorite = favorite) else it }
    }

    /** Drops [uri] and returns the row that went, so the caller can release its grant. */
    suspend fun remove(uri: String): LibraryEntry? {
        var removed: LibraryEntry? = null
        mutate { library ->
            removed = library.entries.firstOrNull { it.uri == uri }
            library.withEntries(library.entries.filterNot { it.uri == uri })
        }
        return removed
    }

    /**
     * Files [uri] under [folderId], or unfiles it when that is null.
     *
     * A folder that does not exist is ignored rather than stored, so a race between
     * deleting a folder and filing into it cannot leave a dangling reference behind.
     */
    suspend fun setFolder(uri: String, folderId: String?) = mutate { library ->
        if (folderId != null && library.folders.none { it.id == folderId }) {
            library
        } else {
            library.mapEntries { if (it.uri == uri) it.copy(folderId = folderId) else it }
        }
    }

    /**
     * Makes a folder called [name], or returns null when [FolderNames] refuses it or the
     * catalogue is already full.
     *
     * Returning null rather than throwing is what lets the dialog show the reason inline
     * and keep what the user typed. The dialog checks the same rules before asking, so a
     * null here means a race, not a validation gap.
     */
    suspend fun createFolder(name: String): LibraryFolder? {
        var created: LibraryFolder? = null
        mutate { library ->
            if (FolderNames.problemWith(name, library.folders) != null) return@mutate library
            if (FolderNames.isFull(library.folders)) return@mutate library

            val folder = LibraryFolder(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                createdAt = System.currentTimeMillis(),
            )
            created = folder
            library.copy(folders = library.folders + folder)
        }
        return created
    }

    /** Renames [id], or returns false when [name] is blank, too long or already taken. */
    suspend fun renameFolder(id: String, name: String): Boolean {
        var renamed = false
        mutate { library ->
            if (library.folders.none { it.id == id }) return@mutate library
            if (FolderNames.problemWith(name, library.folders, excluding = id) != null) {
                return@mutate library
            }
            renamed = true
            library.copy(
                folders = library.folders.map {
                    if (it.id == id) it.copy(name = name.trim()) else it
                },
            )
        }
        return renamed
    }

    /**
     * Forgets a folder. The documents in it are **unfiled, never removed** — deleting a
     * label the user made must not cost them the documents wearing it, and there is no
     * directory to delete either way.
     */
    suspend fun deleteFolder(id: String) = mutate { library ->
        library.copy(folders = library.folders.filterNot { it.id == id })
    }

    /** Everything currently known, newest first. Empty until [load] has run. */
    fun snapshot(): List<LibraryEntry> = (_state.value as? LibraryState.Content)?.entries.orEmpty()

    private suspend fun mutate(transform: (Library) -> Library) {
        mutex.withLock {
            // A mutation can land before load() finishes -- the ViewModel opens a
            // document as soon as an ACTION_VIEW intent arrives. Starting from an empty
            // list there would rewrite the file with one row and drop the rest.
            val current = if (loaded) Library(snapshot(), _folders.value) else readFromDisk()
            val next = tidy(transform(current))
            publish(next)
            write(next, foldersChanged = next.folders != current.folders)
        }
    }

    /** Sorts, unfiles anything pointing at a folder that is gone, then caps. */
    private fun tidy(library: Library): Library {
        val ids = library.folders.mapTo(HashSet()) { it.id }
        val entries = library.entries
            .map { if (it.folderId != null && it.folderId !in ids) it.copy(folderId = null) else it }
            .sortedByDescending { it.lastOpened }
        return library.copy(entries = evict(entries))
    }

    private suspend fun readFromDisk(): Library = withContext(dispatcher) {
        val entries = runCatching { entriesFile.takeIf { it.isFile }?.readText() }
            .getOrNull()
            ?.let(LibraryCodec::decode)
            .orEmpty()
            .sortedByDescending { it.lastOpened }

        val folders = runCatching { foldersFile.takeIf { it.isFile }?.readText() }
            .getOrNull()
            ?.let(LibraryFolderCodec::decode)
            .orEmpty()
            .sortedBy { it.createdAt }
            .take(FolderNames.MAX_FOLDERS)

        Library(entries, folders)
    }

    private suspend fun write(library: Library, foldersChanged: Boolean) = withContext(dispatcher) {
        runCatching {
            directory.mkdirs()
            writeAtomically(entriesFile, LibraryCodec.encode(library.entries))
            // Every open rewrites the entries; the folders almost never change, and
            // rewriting them anyway would be a second file touched on every document.
            if (foldersChanged) {
                writeAtomically(foldersFile, LibraryFolderCodec.encode(library.folders))
            }
        }
    }

    private fun writeAtomically(target: File, encoded: String) {
        val scratch = File(directory, "${target.name}.tmp")
        scratch.writeText(encoded)
        if (!scratch.renameTo(target)) {
            target.writeText(encoded)
            scratch.delete()
        }
    }

    /**
     * Caps the list, but never at a favourite's or a filed document's expense. Starring a
     * document is the user saying they want it kept, and filing it into a folder says the
     * same thing just as deliberately; dropping either because it has not been opened
     * lately would throw away the rows they cared about most — and a folder that quietly
     * empties itself at fifty entries is worse than no folder at all.
     */
    private fun evict(entries: List<LibraryEntry>): List<LibraryEntry> {
        if (entries.size <= MAX_ENTRIES) return entries
        val pinned = entries.filter { it.isFavorite || it.folderId != null }
        val rest = entries.filterNot { it.isFavorite || it.folderId != null }
        val room = (MAX_ENTRIES - pinned.size).coerceAtLeast(0)
        return (pinned + rest.take(room)).sortedByDescending { it.lastOpened }
    }

    private fun publish(library: Library) {
        loaded = true
        _folders.value = library.folders
        _state.value = if (library.entries.isEmpty()) {
            LibraryState.Empty
        } else {
            LibraryState.Content(library.entries)
        }
    }

    /** The two index files as one value, so a mutation can move both under one lock. */
    private data class Library(
        val entries: List<LibraryEntry>,
        val folders: List<LibraryFolder>,
    ) {
        fun withEntries(entries: List<LibraryEntry>) = copy(entries = entries)

        fun mapEntries(transform: (LibraryEntry) -> LibraryEntry) =
            copy(entries = entries.map(transform))
    }

    private val entriesFile: File get() = File(directory, ENTRIES_FILE)
    private val foldersFile: File get() = File(directory, FOLDERS_FILE)

    companion object {
        private const val ENTRIES_FILE = "entries.tsv"
        private const val FOLDERS_FILE = "folders.tsv"

        /**
         * Android caps how many URI grants an app may persist, so an unbounded list
         * would eventually stop being able to reopen anything.
         */
        const val MAX_ENTRIES = 50
    }
}
