package com.mdview.data

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.mdview.markdown.DocumentCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads and writes Markdown documents through the Storage Access Framework.
 *
 * Everything goes through [ContentResolver], so the app needs no storage
 * permissions -- the user's choice in the picker *is* the grant.
 */
class DocumentRepository(private val resolver: ContentResolver) : DocumentSource {

    override suspend fun read(uri: Uri): LoadedDocument = withContext(Dispatchers.IO) {
        // Checked before opening: the whole document ends up in memory and then in a
        // text field, so an accidentally-picked archive should be refused, not loaded.
        val size = sizeOf(uri)
        if (size != null && size > MAX_SIZE_BYTES) {
            throw UnreadableDocumentException(UnreadableDocumentException.Reason.TooLarge)
        }

        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("The document provider returned no data for $uri")

        if (DocumentCodec.looksBinary(bytes)) {
            throw UnreadableDocumentException(UnreadableDocumentException.Reason.NotText)
        }

        val decoded = DocumentCodec.decode(bytes)
        LoadedDocument(decoded.text, decoded.lineEndings, decoded.hadBom)
    }

    override suspend fun write(uri: Uri, document: LoadedDocument) = withContext(Dispatchers.IO) {
        // "wt" truncates first. Plain "w" overwrites in place, which leaves the
        // tail of the previous content behind whenever the new text is shorter.
        resolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(DocumentCodec.encode(document.text, document.lineEndings, document.hadBom))
            output.flush()
        } ?: error("The document provider refused to open $uri for writing")
    }

    override suspend fun displayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    /**
     * Holds on to access across process restarts, and reports how much of it stuck.
     *
     * Read and write are requested separately on purpose. Asking for both at once
     * throws outright when the provider only offered read, which used to leave the app
     * with *no* persisted grant at all -- the document would open once and then be
     * unreachable after a restart.
     *
     * URIs arriving from an ACTION_VIEW intent are usually not persistable in any form,
     * hence [PersistedAccess.None]: still readable for as long as this task lives, but
     * not worth promising the user anything about.
     */
    override fun persistAccess(uri: Uri): PersistedAccess {
        val readWrite = runCatching {
            resolver.takePersistableUriPermission(uri, READ or WRITE)
        }.isSuccess
        if (readWrite) return PersistedAccess.ReadWrite

        val read = runCatching { resolver.takePersistableUriPermission(uri, READ) }.isSuccess
        return if (read) PersistedAccess.ReadOnly else PersistedAccess.None
    }

    /**
     * Releasing with flags that were never taken throws, so the caller has to hand back
     * exactly what [persistAccess] reported.
     */
    override fun releaseAccess(uri: Uri, access: PersistedAccess) {
        val flags = when (access) {
            PersistedAccess.ReadWrite -> READ or WRITE
            PersistedAccess.ReadOnly -> READ
            PersistedAccess.None -> return
        }
        runCatching { resolver.releasePersistableUriPermission(uri, flags) }
    }

    override fun persistedUris(): Set<String> =
        runCatching { resolver.persistedUriPermissions.map { it.uri.toString() }.toSet() }
            .getOrDefault(emptySet())

    /** The provider's reported size, or null when it does not report one. */
    private fun sizeOf(uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (column >= 0 && cursor.moveToFirst() && !cursor.isNull(column)) {
                cursor.getLong(column)
            } else {
                null
            }
        }
    }.getOrNull()

    private companion object {
        /** Comfortably larger than any hand-written document, small enough to stay responsive. */
        const val MAX_SIZE_BYTES = 2L * 1024 * 1024

        const val READ = Intent.FLAG_GRANT_READ_URI_PERMISSION
        const val WRITE = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
