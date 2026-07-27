package com.mdview.data

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads and writes Markdown documents through the Storage Access Framework.
 *
 * Everything goes through [ContentResolver], so the app needs no storage
 * permissions -- the user's choice in the picker *is* the grant.
 */
class DocumentRepository(private val resolver: ContentResolver) {

    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        } ?: error("The document provider returned no data for $uri")
    }

    suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        // "wt" truncates first. Plain "w" overwrites in place, which leaves the
        // tail of the previous content behind whenever the new text is shorter.
        resolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.flush()
        } ?: error("The document provider refused to open $uri for writing")
    }

    suspend fun displayName(uri: Uri): String? = withContext(Dispatchers.IO) {
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
    fun persistAccess(uri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }
}
