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
     * Holds on to read/write access across process restarts. Best effort: URIs that
     * arrive from an ACTION_VIEW intent are usually not persistable, and that is fine
     * -- the document stays readable for as long as this task lives.
     */
    override fun persistAccess(uri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

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
    }
}
