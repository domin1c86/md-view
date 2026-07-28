package com.mdview.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads a skin file the user picked through the Storage Access Framework.
 *
 * Separate from [DocumentRepository] rather than an extra method on `DocumentSource`:
 * that interface's contract is "a Markdown document the user is editing", and widening
 * it to mean "any file at all" would put a skin through the draft and library machinery.
 * The size pre-check is the same idea though -- ask the provider first, so an
 * accidentally-picked video is refused rather than pulled into memory.
 */
class SkinImporter(private val resolver: ContentResolver) {

    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        val size = sizeOf(uri)
        if (size != null && size > SkinCodec.MAX_CHARS) {
            throw InvalidSkinException(InvalidSkinException.Reason.TooLarge)
        }

        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw InvalidSkinException(InvalidSkinException.Reason.Malformed)

        // Checked again after reading: providers are free to under-report, or to report
        // nothing at all, and the limit is what keeps the parser's bounds meaningful.
        if (bytes.size > SkinCodec.MAX_CHARS) {
            throw InvalidSkinException(InvalidSkinException.Reason.TooLarge)
        }

        bytes.decodeToString()
    }

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
}
