package com.mdview.markdown

import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.net.toUri
import com.mdview.data.FolderGrant
import com.mdview.data.TreeImageResolver

/** What Coil should be handed for an image, or why it cannot be handed anything. */
internal sealed interface ImageModel {

    /** Fetch this. A String for remote and already-resolvable schemes, a Uri once built. */
    data class Fetch(val model: Any) : ImageModel

    /** A path into the document's folder, which no granted folder covers yet. */
    data object NeedsFolder : ImageModel

    /**
     * A folder is granted, but it cannot serve this path: it does not contain the
     * document, the path climbs out of it, or the provider's ids carry no path at all.
     */
    data object WrongFolder : ImageModel

    /** Nothing may be built from this destination. */
    data object Unusable : ImageModel
}

/**
 * How the document on screen turns an image destination into something fetchable, and how
 * it asks for the folder access it needs.
 *
 * A CompositionLocal rather than a parameter, matching `LocalSkin` and `LocalRemoteImages`:
 * `MarkdownImage` sits several composables below anything that knows which document is
 * open, and threading a resolver through every block would touch code that has no interest
 * in images.
 */
internal interface DocumentImages {

    /** Pure string and Uri arithmetic — no I/O, so it is safe to call during composition. */
    fun modelFor(destination: String?): ImageModel

    /** False on the dashboard and under `@Preview`, where there is no picker to launch. */
    val canRequestFolder: Boolean

    fun requestFolder()

    /**
     * Resolves nothing and offers nothing.
     *
     * The default, so `@Preview` still draws and a forgotten provider degrades to today's
     * behaviour — a placeholder — rather than crashing.
     */
    object None : DocumentImages {
        override fun modelFor(destination: String?): ImageModel =
            when (ImagePath.classify(destination)) {
                ImageTarget.Remote, ImageTarget.Direct -> ImageModel.Fetch(destination.orEmpty())
                is ImageTarget.Local -> ImageModel.NeedsFolder
                ImageTarget.Unusable -> ImageModel.Unusable
            }

        override val canRequestFolder: Boolean = false
        override fun requestFolder() = Unit
    }
}

internal val LocalDocumentImages = staticCompositionLocalOf<DocumentImages> { DocumentImages.None }

/**
 * Resolves an image against whichever granted folder contains the open document.
 *
 * [grants] is the whole list rather than one match because the answer depends on the
 * destination: a document may sit inside two nested grants, and which one applies is
 * decided per image by [ImageTarget.Base].
 */
internal class GrantedFolderImages(
    private val documentUri: Uri?,
    private val grants: List<FolderGrant>,
    private val onRequestFolder: () -> Unit,
) : DocumentImages {

    /**
     * The granted trees containing the document, outermost first.
     *
     * **Shortest document id wins.** With both `notes/` and `notes/blog/` granted, a
     * `/images/hero.png` inside `notes/blog/post.md` means `notes/images/hero.png` — the
     * outermost grant is the closest thing to a site root, which is exactly what a leading
     * slash was taken to mean. Longest-match would resolve it in the wrong folder.
     */
    private val covering: List<Uri> by lazy {
        val document = documentUri ?: return@lazy emptyList()
        grants.asSequence()
            .mapNotNull { grant -> runCatching { grant.treeUri.toUri() }.getOrNull() }
            .filter { TreeImageResolver.covers(it, document) }
            .sortedBy { it.toString().length }
            .toList()
    }

    override fun modelFor(destination: String?): ImageModel {
        val target = ImagePath.classify(destination)
        return when (target) {
            ImageTarget.Remote, ImageTarget.Direct -> ImageModel.Fetch(destination.orEmpty())
            ImageTarget.Unusable -> ImageModel.Unusable
            is ImageTarget.Local -> {
                val document = documentUri ?: return ImageModel.NeedsFolder
                if (covering.isEmpty()) return ImageModel.NeedsFolder

                covering.firstNotNullOfOrNull { tree ->
                    TreeImageResolver.childUri(tree, document, target.base, target.path)
                }?.let(ImageModel::Fetch) ?: ImageModel.WrongFolder
            }
        }
    }

    override val canRequestFolder: Boolean = true

    override fun requestFolder() = onRequestFolder()
}
