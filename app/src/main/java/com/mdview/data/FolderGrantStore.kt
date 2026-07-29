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
 * The folders the user has granted MdView read access to, so documents can show their
 * sibling images.
 *
 * Shaped like [LibraryStore]: a plain [File] rather than a Context so it tests on the JVM,
 * an injectable [dispatcher], one index file rewritten in full behind a [Mutex], and an
 * atomic temp-file-plus-rename.
 *
 * Unlike [SkinStore] this is *not* one file per row. A tree URI is not a legal filename,
 * and the order matters: when two granted folders both contain a document, the outermost
 * one wins, and that tie-break needs a stable list.
 *
 * There is no `Loading`/`Empty`/`Content` state here as there is for the library. An empty
 * list has only one rendering — the Mine tab's "none yet" caption — so a sealed state
 * would be three cases where one suffices.
 */
class FolderGrantStore(
    private val directory: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val mutex = Mutex()
    private var loaded = false
    private val _grants = MutableStateFlow<List<FolderGrant>>(emptyList())

    /** Newest grant first. Empty until [load] has run. */
    val grants: StateFlow<List<FolderGrant>> = _grants.asStateFlow()

    /** Reads the file into memory. Safe to call more than once. */
    suspend fun load() {
        mutex.withLock { publish(readFromDisk()) }
    }

    /**
     * Remembers [grant], replacing any earlier grant of the same folder.
     *
     * Returns the rows the cap pushed out, so the caller can release their URI permissions
     * — a grant the app can no longer list is one it can never revoke either.
     */
    suspend fun add(grant: FolderGrant): List<FolderGrant> {
        var evicted = emptyList<FolderGrant>()
        mutate { current ->
            val kept = listOf(grant) + current.filterNot { it.treeUri == grant.treeUri }
            evicted = kept.drop(MAX_GRANTS)
            kept.take(MAX_GRANTS)
        }
        return evicted
    }

    /** Forgets [treeUri] and returns the row that went, so its grant can be released. */
    suspend fun remove(treeUri: String): FolderGrant? {
        var removed: FolderGrant? = null
        mutate { current ->
            removed = current.firstOrNull { it.treeUri == treeUri }
            current.filterNot { it.treeUri == treeUri }
        }
        return removed
    }

    /**
     * Drops rows the system no longer honours.
     *
     * URI grants outlive the app but not everything: clearing the provider's data,
     * ejecting a card or deleting the folder all revoke one silently. A stale row would
     * list a folder in the Mine tab that does nothing and would build child URIs that fail
     * with no explanation.
     *
     * [live] must be a real reading of the platform's persisted permissions. Callers that
     * cannot obtain one must skip this entirely rather than pass an empty set, which would
     * wipe every grant on a transient failure.
     */
    suspend fun reconcile(live: Set<String>) {
        mutate { current -> current.filter { it.treeUri in live } }
    }

    /** Everything currently known, newest first. Empty until [load] has run. */
    fun snapshot(): List<FolderGrant> = _grants.value

    private suspend fun mutate(transform: (List<FolderGrant>) -> List<FolderGrant>) {
        mutex.withLock {
            // A mutation can land before load() finishes -- a document opened straight
            // from an ACTION_VIEW intent can reach an image before the read returns.
            // Starting from an empty list here would rewrite the file with one row.
            val current = if (loaded) _grants.value else readFromDisk()
            val next = transform(current)
            publish(next)
            write(next)
        }
    }

    private suspend fun readFromDisk(): List<FolderGrant> = withContext(dispatcher) {
        runCatching { file.takeIf { it.isFile }?.readText() }
            .getOrNull()
            ?.let(FolderGrantCodec::decode)
            .orEmpty()
            .sortedByDescending { it.grantedAt }
            .take(MAX_GRANTS)
    }

    private suspend fun write(grants: List<FolderGrant>) = withContext(dispatcher) {
        runCatching {
            directory.mkdirs()
            val encoded = FolderGrantCodec.encode(grants)
            val scratch = File(directory, "$FILE_NAME.tmp")
            scratch.writeText(encoded)
            if (!scratch.renameTo(file)) {
                file.writeText(encoded)
                scratch.delete()
            }
        }
    }

    private fun publish(grants: List<FolderGrant>) {
        loaded = true
        _grants.value = grants
    }

    private val file: File get() = File(directory, FILE_NAME)

    companion object {
        private const val FILE_NAME = "grants.tsv"

        /**
         * A grant covers a whole tree, so ten distinct roots is already generous. The cap
         * exists because Android bounds how many URI permissions one app may persist, and
         * [LibraryStore.MAX_ENTRIES] already spends part of that budget.
         */
        const val MAX_GRANTS = 10
    }
}
