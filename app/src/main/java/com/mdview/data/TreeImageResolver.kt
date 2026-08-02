package com.mdview.data

import android.net.Uri
import android.provider.DocumentsContract
import com.mdview.markdown.ImageTarget
import com.mdview.markdown.LocalPath

/**
 * Turns a granted folder plus an image path into a `content://` URI that grant covers.
 *
 * Every function here is pure `Uri` path-segment arithmetic — `getDocumentId`,
 * `getTreeDocumentId` and `buildDocumentUriUsingTree` all build strings and touch no
 * Binder. That is what lets the whole resolve happen during composition, the same way
 * `isRemoteImage` does, with no coroutine and no cache.
 *
 * All of it is wrapped in `runCatching` because `getDocumentId` throws
 * `IllegalArgumentException` on a URI that is not a document URI — exactly what an
 * `ACTION_VIEW` intent from a gallery or a mail client delivers.
 */
internal object TreeImageResolver {

    /** Whether [treeUri]'s grant reaches [docUri]. */
    fun covers(treeUri: Uri, docUri: Uri): Boolean {
        if (treeUri.authority != docUri.authority) return false
        val treeId = treeDocumentId(treeUri) ?: return false
        val docId = documentId(docUri) ?: return false
        return TreeDocumentIds.covers(treeId, docId)
    }

    /**
     * The document URI of an image, or null when this tree cannot serve it — because the
     * document is not inside it, because the path climbs out of it, or because the
     * provider's ids carry no path to do arithmetic on.
     */
    fun childUri(
        treeUri: Uri,
        docUri: Uri,
        base: ImageTarget.Base,
        path: LocalPath,
    ): Uri? {
        if (treeUri.authority != docUri.authority) return null
        val treeId = treeDocumentId(treeUri) ?: return null
        val docId = documentId(docUri) ?: return null
        val childId = TreeDocumentIds.childId(treeId, docId, base, path) ?: return null

        // Built *using the tree*, which is what makes the grant apply. The plain
        // buildDocumentUri form would name the same file and be unreadable.
        return runCatching { DocumentsContract.buildDocumentUriUsingTree(treeUri, childId) }.getOrNull()
    }

    /**
     * The image's URI built from a tree that covers **the image**, not the document.
     *
     * This is the one that makes granting the folder the pictures are actually in work.
     * [childUri] can only answer when the grant contains the document, which meant a
     * document beside an `image/` folder needed the *parent* granted — a strictly wider
     * permission than the figure needs, and not what anyone reaches for when the notice
     * appears on the image itself.
     *
     * Only for [ImageTarget.Base.DocumentFolder]. A leading slash is defined as the
     * granted tree's root, so it has no meaning until a tree covering the document says
     * where that root is; [childUri] still owns that case.
     */
    fun siblingUri(treeUri: Uri, docUri: Uri, path: LocalPath): Uri? {
        if (treeUri.authority != docUri.authority) return null
        val treeId = treeDocumentId(treeUri) ?: return null
        val docId = documentId(docUri) ?: return null
        val imageId = TreeDocumentIds.siblingId(docId, path) ?: return null

        // The grant has to reach the image itself. Everything the separator-aware prefix
        // guards against applies here unchanged.
        if (!TreeDocumentIds.covers(treeId, imageId)) return null

        return runCatching { DocumentsContract.buildDocumentUriUsingTree(treeUri, imageId) }.getOrNull()
    }

    private fun treeDocumentId(treeUri: Uri): String? = runCatching {
        if (!DocumentsContract.isTreeUri(treeUri)) return null
        DocumentsContract.getTreeDocumentId(treeUri)
    }.getOrNull()

    /**
     * The document id of [uri], whether it was picked on its own or reached through a tree.
     *
     * A URI that has been through [childUri] carries both a tree id and a document id;
     * `getDocumentId` returns the latter, which is the one that names the file.
     */
    private fun documentId(uri: Uri): String? = runCatching {
        DocumentsContract.getDocumentId(uri)
    }.getOrNull()
}
