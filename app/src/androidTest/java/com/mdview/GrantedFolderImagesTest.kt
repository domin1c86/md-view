package com.mdview

import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mdview.data.FolderGrant
import com.mdview.markdown.GrantedFolderImages
import com.mdview.markdown.ImageModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Which granted folder is enough to draw a figure.
 *
 * Instrumented rather than JVM because every input is a real [Uri] and the answers come
 * from `DocumentsContract`, neither of which works off-device. No Activity, no Compose and
 * no storage: this drives the resolver directly.
 *
 * The rule under test is that **a grant has to reach the image**, not the document. It used
 * to be the other way round, which meant a reader who granted the `image/` folder their
 * pictures were in got the same "choose the folder this document is in" notice back, with
 * nothing to say what had gone wrong.
 */
@RunWith(AndroidJUnit4::class)
class GrantedFolderImagesTest {

    private val authority = "com.android.externalstorage.documents"

    private fun document(id: String): Uri = DocumentsContract.buildDocumentUri(authority, id)

    private fun grant(id: String) = FolderGrant(
        treeUri = DocumentsContract.buildTreeDocumentUri(authority, id).toString(),
        displayName = id.substringAfterLast('/'),
        grantedAt = 0L,
    )

    private fun resolver(document: Uri?, vararg grants: FolderGrant) =
        GrantedFolderImages(document, grants.toList()) {}

    private fun ImageModel.uri(): Uri {
        assertTrue("expected a fetchable image, got $this", this is ImageModel.Fetch)
        val model = (this as ImageModel.Fetch).model
        assertTrue("expected a Uri model, got ${model::class}", model is Uri)
        return model as Uri
    }

    @Test
    fun aGrantOnTheImagesOwnFolderIsEnough() {
        // Exactly the reported case: post.md sits beside an image/ folder, and only that
        // folder was granted.
        val model = resolver(
            document("primary:Documents/post.md"),
            grant("primary:Documents/image"),
        ).modelFor("./image/paris.jpg")

        assertEquals(
            "primary:Documents/image/paris.jpg",
            DocumentsContract.getDocumentId(model.uri()),
        )
    }

    @Test
    fun aGrantOnTheDocumentsFolderStillWorks() {
        // The behaviour that already shipped must survive the widening.
        val model = resolver(
            document("primary:Documents/post.md"),
            grant("primary:Documents"),
        ).modelFor("./image/paris.jpg")

        assertEquals(
            "primary:Documents/image/paris.jpg",
            DocumentsContract.getDocumentId(model.uri()),
        )
    }

    @Test
    fun anUnrelatedGrantSaysSoRatherThanAskingAgain() {
        // The distinction that was missing. Someone who has just granted a folder must not
        // be handed the identical invitation they only now accepted.
        val model = resolver(
            document("primary:Documents/post.md"),
            grant("primary:Pictures"),
        ).modelFor("./image/paris.jpg")

        assertEquals(ImageModel.WrongFolder, model)
    }

    @Test
    fun noGrantsAtAllStillAsksForOne() {
        assertEquals(
            ImageModel.NeedsFolder,
            resolver(document("primary:Documents/post.md")).modelFor("./image/paris.jpg"),
        )
    }

    @Test
    fun aPathClimbingOutOfEveryGrantResolvesNothing() {
        // The traversal refusal has to survive being computed from the document instead of
        // from the tree: ../../secret.png must never be served by an image/ grant.
        val model = resolver(
            document("primary:Documents/post.md"),
            grant("primary:Documents/image"),
        ).modelFor("../../secret.png")

        assertEquals(ImageModel.WrongFolder, model)
    }

    @Test
    fun aLeadingSlashStillNeedsAFolderContainingTheDocument() {
        // A leading slash means the granted tree's root, so a grant that holds only the
        // image cannot say where that root is.
        assertEquals(
            ImageModel.WrongFolder,
            resolver(
                document("primary:Documents/post.md"),
                grant("primary:Documents/image"),
            ).modelFor("/image/paris.jpg"),
        )

        // With the document's folder granted it resolves from that root.
        val fromRoot = resolver(
            document("primary:Documents/post.md"),
            grant("primary:Documents"),
        ).modelFor("/image/paris.jpg")

        assertEquals(
            "primary:Documents/image/paris.jpg",
            DocumentsContract.getDocumentId(fromRoot.uri()),
        )
    }

    @Test
    fun aLookalikeFolderNameDoesNotCount() {
        // image2/ shares a prefix with image/ and must not be spliced into.
        assertEquals(
            ImageModel.WrongFolder,
            resolver(
                document("primary:Documents/post.md"),
                grant("primary:Documents/image2"),
            ).modelFor("./image/paris.jpg"),
        )
    }

    @Test
    fun remoteAndDirectDestinationsNeverAskForAFolder() {
        val images = resolver(document("primary:Documents/post.md"))
        assertEquals(
            ImageModel.Fetch("https://example.com/a.png"),
            images.modelFor("https://example.com/a.png"),
        )
        assertEquals(
            ImageModel.Fetch("data:image/png;base64,AAAA"),
            images.modelFor("data:image/png;base64,AAAA"),
        )
    }
}
