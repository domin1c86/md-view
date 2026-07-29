package com.mdview.data

/**
 * A folder the user has let MdView read images from.
 *
 * [treeUri] is a String rather than a `Uri` for the same reason [LibraryEntry.uri] is:
 * `Uri.parse` returns null under `unitTests.isReturnDefaultValues`, and keeping the type
 * plain is what lets [FolderGrantStore] be tested on the JVM.
 *
 * The grant is *transitive*: it covers every folder beneath [treeUri], not just the one
 * document that prompted it. That is the point — one grant serves a whole notes tree —
 * but it is also why the Mine tab lists them and why they can be revoked.
 */
data class FolderGrant(
    val treeUri: String,
    val displayName: String,
    val grantedAt: Long,
)
