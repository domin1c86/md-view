package com.mdview.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Keeps a private copy of whatever the user has typed but not saved.
 *
 * Android kills backgrounded processes whenever it likes, and it does not ask first.
 * Without this, every unsaved edit is one memory-pressure event away from being gone:
 * the ViewModel only persists the document URI, so a restore re-reads the file from
 * disk and silently discards the buffer.
 *
 * Drafts live in app-private storage, so no permission and no user-visible clutter.
 * Takes a plain [File] rather than a Context so it can be tested against a temp dir,
 * and an injectable [dispatcher] so tests can observe writes without racing them.
 */
class DraftStore(
    private val directory: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Stores [text] against [key], replacing any previous draft. */
    suspend fun save(key: String, text: String) {
        withContext(dispatcher) {
            runCatching {
                directory.mkdirs()
                // Write beside the target and rename: a process death midway through
                // then leaves the previous draft intact instead of a half-written one.
                val destination = fileFor(key)
                val scratch = File(directory, "${destination.name}.tmp")
                scratch.writeText(text)
                if (!scratch.renameTo(destination)) {
                    destination.writeText(text)
                    scratch.delete()
                }
            }
        }
    }

    /** The draft stored against [key], or null when there is none. */
    suspend fun load(key: String): String? = withContext(dispatcher) {
        runCatching { fileFor(key).takeIf { it.isFile }?.readText() }.getOrNull()
    }

    /** Drops the draft for [key]. Called once its content has reached the real file. */
    suspend fun clear(key: String) {
        withContext(dispatcher) { runCatching { fileFor(key).delete() } }
    }

    private fun fileFor(key: String) = File(directory, "$key.md")

    companion object {
        /** Where a document that has never been saved anywhere keeps its draft. */
        const val UNTITLED_KEY = "untitled"

        /**
         * A filename-safe key for [uri]. Hashed rather than escaped because document
         * URIs are long, arbitrary, and full of characters a filename cannot hold.
         */
        fun keyFor(uri: String?): String {
            if (uri == null) return UNTITLED_KEY
            val digest = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
