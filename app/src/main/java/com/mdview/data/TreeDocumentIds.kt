package com.mdview.data

import com.mdview.markdown.ImageTarget
import com.mdview.markdown.LocalPath

/**
 * The string half of resolving an image against a granted folder.
 *
 * Kept apart from the `Uri` half in `TreeImageResolver` so that the prefix rule and the
 * climb budget — the two parts that are actually easy to get wrong — sit in the JVM test
 * suite rather than needing an emulator.
 *
 * Document ids are provider-defined. `ExternalStorageProvider`, which serves every local
 * file this feature exists for, writes a volume label, a colon, then `/`-separated names:
 *
 * ```
 * tree      primary:Documents/notes
 * document  primary:Documents/notes/2026/post.md
 * ```
 *
 * A whole-volume grant is `primary:` with no trailing name, and the Downloads provider
 * writes `raw:/storage/emulated/0/Download`. Both are why the separator is decided per id
 * rather than assumed.
 *
 * Providers whose ids are opaque — Drive, and the media provider's `image:1234` — simply
 * fail [covers] and resolve nothing. That is the correct answer rather than a gap: there
 * is no path arithmetic that could be right on an id with no path in it, and failing here
 * is what stops a wrong document id ever being built.
 */
internal object TreeDocumentIds {

    /**
     * What joins [treeId] to a name beneath it.
     *
     * `primary:` already ends in its own separator, so appending another would produce
     * `primary:/Documents`, which names nothing.
     */
    private fun joiner(treeId: String): String =
        if (treeId.endsWith(':') || treeId.endsWith('/')) "" else "/"

    /**
     * Whether [docId] names something at or beneath [treeId].
     *
     * The separator matters: a plain `startsWith` lets the tree `primary:Doc` "cover" the
     * document `primary:Documents/x.md` and then splice children into a folder the user
     * never granted.
     */
    fun covers(treeId: String, docId: String): Boolean =
        docId == treeId || docId.startsWith(treeId + joiner(treeId))

    /**
     * The segments of [docId]'s own folder, relative to [treeId], or null when not covered.
     */
    fun folderOf(treeId: String, docId: String): List<String>? {
        if (!covers(treeId, docId)) return null
        val relative = docId.removePrefix(treeId).removePrefix("/")
        return relative.split('/').filter { it.isNotEmpty() }.dropLast(1)
    }

    /**
     * The document id an image path names, worked out from the document's id alone.
     *
     * No tree is involved, and that is the point. The access a figure actually needs is
     * permission to read *the image*, not permission to read the folder the document
     * happens to sit in — so the id is computed first and matched against the grants
     * afterwards. Requiring a grant over the document made the obvious move, granting the
     * `image/` folder the pictures are in, fail with the same message that asked for it.
     *
     * The climb is still refused rather than clamped, exactly as in [childId]: [path] may
     * walk up to the volume root and no further, because there is no id above it to name.
     */
    fun siblingId(docId: String, path: LocalPath): String? {
        val colon = docId.indexOf(':')
        val prefix = if (colon >= 0) docId.substring(0, colon + 1) else ""
        val rest = if (colon >= 0) docId.substring(colon + 1) else docId
        // `raw:/storage/...` is rooted and has to stay rooted; `primary:Documents` is not.
        val rooted = rest.startsWith('/')

        val segments = rest.split('/').filter { it.isNotEmpty() }
        // A document id with no name in it is a volume, not a file, so it has no folder.
        if (segments.isEmpty()) return null

        val folder = segments.dropLast(1)
        if (path.ascend > folder.size) return null

        val resolved = folder.dropLast(path.ascend) + path.segments
        if (resolved.isEmpty()) return null
        return prefix + (if (rooted) "/" else "") + resolved.joinToString("/")
    }

    /**
     * The document id of an image, or null when [path] leaves the granted tree.
     *
     * Returning null rather than clamping is the same call [com.mdview.markdown.ImagePath]
     * makes about `..`: a path that climbs out of the grant names a file the user did not
     * share, and quietly resolving it somewhere else would be worse than showing nothing.
     */
    fun childId(
        treeId: String,
        docId: String,
        base: ImageTarget.Base,
        path: LocalPath,
    ): String? {
        val folder = folderOf(treeId, docId) ?: return null
        val start = when (base) {
            ImageTarget.Base.DocumentFolder -> folder
            ImageTarget.Base.TreeRoot -> emptyList()
        }
        if (path.ascend > start.size) return null

        val segments = start.dropLast(path.ascend) + path.segments
        return treeId + joiner(treeId) + segments.joinToString("/")
    }
}
