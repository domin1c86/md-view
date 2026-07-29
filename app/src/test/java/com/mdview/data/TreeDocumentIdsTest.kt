package com.mdview.data

import com.mdview.markdown.ImageTarget.Base
import com.mdview.markdown.LocalPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Splicing an image's path into a SAF document id without leaving the granted folder. */
class TreeDocumentIdsTest {

    private fun child(
        treeId: String,
        docId: String,
        base: Base,
        vararg segments: String,
        ascend: Int = 0,
    ): String? = TreeDocumentIds.childId(
        treeId,
        docId,
        base,
        LocalPath(ascend, segments.toList()),
    )

    @Test
    fun `a document directly inside the granted folder has no folder of its own`() {
        assertEquals(
            emptyList<String>(),
            TreeDocumentIds.folderOf("primary:Documents", "primary:Documents/post.md"),
        )
    }

    @Test
    fun `a document further down reports the folders between it and the root`() {
        assertEquals(
            listOf("notes", "2026"),
            TreeDocumentIds.folderOf("primary:Documents", "primary:Documents/notes/2026/post.md"),
        )
    }

    @Test
    fun `a sibling image resolves beside the document`() {
        assertEquals(
            "primary:Documents/notes/images/pic.png",
            child(
                "primary:Documents",
                "primary:Documents/notes/post.md",
                Base.DocumentFolder,
                "images", "pic.png",
            ),
        )
    }

    @Test
    fun `a leading slash resolves from the granted root, not the document's folder`() {
        assertEquals(
            "primary:Documents/images/hero.png",
            child(
                "primary:Documents",
                "primary:Documents/notes/2026/post.md",
                Base.TreeRoot,
                "images", "hero.png",
            ),
        )
    }

    @Test
    fun `a climb inside the tree splices from the right folder`() {
        assertEquals(
            "primary:Documents/notes/assets/x.png",
            child(
                "primary:Documents",
                "primary:Documents/notes/2026/post.md",
                Base.DocumentFolder,
                "assets", "x.png",
                ascend = 1,
            ),
        )
    }

    @Test
    fun `a climb past the granted root resolves to nothing`() {
        // The document is one folder deep, so two climbs leave the grant entirely.
        assertNull(
            child(
                "primary:Documents",
                "primary:Documents/notes/post.md",
                Base.DocumentFolder,
                "x.png",
                ascend = 2,
            ),
        )
    }

    @Test
    fun `a whole-volume grant does not gain a second separator`() {
        assertTrue(TreeDocumentIds.covers("primary:", "primary:Documents/post.md"))
        assertEquals(
            "primary:Documents/pic.png",
            child("primary:", "primary:Documents/post.md", Base.DocumentFolder, "pic.png"),
        )
    }

    @Test
    fun `a downloads-style raw id works the same way`() {
        val tree = "raw:/storage/emulated/0/Download"
        assertTrue(TreeDocumentIds.covers(tree, "$tree/post.md"))
        assertEquals(
            "$tree/img/a.png",
            child(tree, "$tree/post.md", Base.DocumentFolder, "img", "a.png"),
        )
    }

    @Test
    fun `a shared name prefix is not containment`() {
        // The bug this rule exists for: `startsWith` alone would splice children of
        // `primary:Documents` into the unrelated folder `primary:Doc`.
        assertFalse(TreeDocumentIds.covers("primary:Doc", "primary:Documents/x.md"))
        assertNull(
            child("primary:Doc", "primary:Documents/x.md", Base.DocumentFolder, "a.png"),
        )
    }

    @Test
    fun `a folder is covered by itself`() {
        assertTrue(TreeDocumentIds.covers("primary:Documents", "primary:Documents"))
    }

    @Test
    fun `opaque provider ids resolve nothing rather than resolving wrongly`() {
        // Drive-shaped: the tree id and the document id have no relationship a path could
        // be measured along. Failing here is what stops a wrong id ever being built.
        assertFalse(TreeDocumentIds.covers("0AKp9xQvT", "1cB7_zLmNq4"))
        assertNull(child("0AKp9xQvT", "1cB7_zLmNq4", Base.DocumentFolder, "a.png"))
        // The media provider's ids look like a scheme but carry no path.
        assertFalse(TreeDocumentIds.covers("image:1024", "image:2048"))
    }

    @Test
    fun `a document outside the grant resolves nothing`() {
        assertNull(
            child("primary:Pictures", "primary:Documents/post.md", Base.DocumentFolder, "a.png"),
        )
    }
}
