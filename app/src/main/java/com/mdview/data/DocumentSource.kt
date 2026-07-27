package com.mdview.data

import android.net.Uri
import com.mdview.markdown.DocumentCodec

/** A document as it was found on disk, with the details needed to write it back unchanged. */
data class LoadedDocument(
    val text: String,
    val lineEndings: DocumentCodec.LineEndings = DocumentCodec.LineEndings.Lf,
    val hadBom: Boolean = false,
)

/** Raised when a file is refused before it is read, with a reason worth showing the user. */
class UnreadableDocumentException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { TooLarge, NotText }
}

/**
 * The document operations the ViewModel needs, without the Android plumbing.
 *
 * Exists so [DocumentRepository]'s ContentResolver work can be swapped for a fake in
 * JVM tests -- open, save and dirty-tracking is where unsaved work gets lost, so it is
 * the logic that most needs covering.
 */
interface DocumentSource {
    suspend fun read(uri: Uri): LoadedDocument
    suspend fun write(uri: Uri, document: LoadedDocument)
    suspend fun displayName(uri: Uri): String?
    fun persistAccess(uri: Uri)
}
