package com.mdview.data

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Taking, naming and handing back the folder grants that let a document show its images.
 *
 * Deliberately separate from [DocumentRepository], which is about *the* document: this
 * takes a tree permission rather than a document one, and never writes.
 */
class FolderAccess(private val resolver: ContentResolver) {

    /**
     * Holds on to [treeUri] across process restarts.
     *
     * Read only, not read-and-write: images are never written, and [DocumentRepository]
     * already records that asking for both at once throws against a read-only provider and
     * persists nothing at all. Read alone is always a subset of what `OpenDocumentTree`
     * hands back, so it cannot fail for want of a flag.
     */
    fun persist(treeUri: Uri): Boolean =
        runCatching { resolver.takePersistableUriPermission(treeUri, READ) }.isSuccess

    fun release(treeUri: Uri) {
        runCatching { resolver.releasePersistableUriPermission(treeUri, READ) }
    }

    /**
     * The folder's own name, for the Mine tab.
     *
     * One Binder round trip, made once when the grant is taken rather than every time the
     * list is drawn. Falls back to the last piece of the tree's document id, which for the
     * providers that matter here is the folder name anyway.
     */
    suspend fun displayName(treeUri: Uri): String = withContext(Dispatchers.IO) {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
        val queried = documentId?.let { id ->
            runCatching {
                val document = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                resolver.query(document, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
                    }
            }.getOrNull()
        }

        queried?.takeIf { it.isNotBlank() }
            ?: documentId?.substringAfterLast('/')?.substringAfterLast(':')?.takeIf { it.isNotBlank() }
            ?: treeUri.lastPathSegment.orEmpty()
    }

    /** Every tree URI the platform still honours, for reconciling the store against. */
    fun persistedTrees(): Set<String>? = runCatching {
        resolver.persistedUriPermissions
            .map { it.uri }
            .filter { DocumentsContract.isTreeUri(it) }
            .map { it.toString() }
            .toSet()
    }.getOrNull()

    private companion object {
        const val READ = Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
}
