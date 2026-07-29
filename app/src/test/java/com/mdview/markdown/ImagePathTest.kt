package com.mdview.markdown

import com.mdview.markdown.ImageTarget.Base
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The classification a folder grant is later measured against.
 *
 * Roughly half of these are traversal cases. They are not paranoia: the output of this
 * object is spliced into a SAF document id, and a segment that slips through validation
 * is a read of a file the user never granted.
 */
class ImagePathTest {

    private fun local(destination: String): ImageTarget.Local =
        ImagePath.classify(destination) as? ImageTarget.Local
            ?: error("expected a local path for \"$destination\", got ${ImagePath.classify(destination)}")

    private fun assertUnusable(destination: String) {
        assertEquals("\"$destination\"", ImageTarget.Unusable, ImagePath.classify(destination))
    }

    @Test
    fun `a bare filename is relative to the document's folder`() {
        val target = local("pic.png")
        assertEquals(Base.DocumentFolder, target.base)
        assertEquals(LocalPath(ascend = 0, segments = listOf("pic.png")), target.path)
    }

    @Test
    fun `an explicit dot prefix means the same thing`() {
        assertEquals(local("pic.png"), local("./pic.png"))
    }

    @Test
    fun `a nested relative path keeps its segments in order`() {
        val target = local("./images/screens/pic.png")
        assertEquals(
            LocalPath(ascend = 0, segments = listOf("images", "screens", "pic.png")),
            target.path,
        )
    }

    @Test
    fun `a leading slash is measured from the granted folder's root`() {
        val target = local("/images/pic.png")
        assertEquals(Base.TreeRoot, target.base)
        assertEquals(LocalPath(ascend = 0, segments = listOf("images", "pic.png")), target.path)
    }

    @Test
    fun `dot dot is reported as a climb rather than applied`() {
        assertEquals(LocalPath(ascend = 1, segments = listOf("assets", "x.png")), local("../assets/x.png").path)
        assertEquals(LocalPath(ascend = 2, segments = listOf("x.png")), local("../../x.png").path)
    }

    @Test
    fun `dot dot cancels a segment it can reach`() {
        // `images/../x.png` never leaves the document's folder, so nothing is climbed.
        assertEquals(LocalPath(ascend = 0, segments = listOf("x.png")), local("images/../x.png").path)
    }

    @Test
    fun `climbing above the granted root is refused rather than clamped`() {
        // Clamping to `x.png` would silently name a different file, and might succeed.
        assertUnusable("/../x.png")
        assertUnusable("/images/../../x.png")
    }

    @Test
    fun `empty and dot segments collapse`() {
        assertEquals(local("a/b.png"), local("a//b.png"))
        assertEquals(local("a/b.png"), local("./a/./b.png"))
    }

    @Test
    fun `a query and a fragment are stripped`() {
        val expected = LocalPath(ascend = 0, segments = listOf("a.png"))
        assertEquals(expected, local("a.png?v=2").path)
        assertEquals(expected, local("a.png#anchor").path)
        assertEquals(expected, local("a.png?v=2#anchor").path)
    }

    @Test
    fun `percent escapes are decoded a byte at a time`() {
        assertEquals(listOf("my photo.png"), local("my%20photo.png").path.segments)
        // Three bytes, one character. Decoding per-byte into chars would give mojibake.
        assertEquals(listOf("中文.png"), local("%E4%B8%AD%E6%96%87.png").path.segments)
    }

    @Test
    fun `an invalid escape is kept as written`() {
        assertEquals(listOf("100%zz.png"), local("100%zz.png").path.segments)
        assertEquals(listOf("trailing%.png"), local("trailing%.png").path.segments)
    }

    @Test
    fun `an encoded separator cannot smuggle in a second segment`() {
        // This is the whole reason decoding happens after the split and not before.
        assertUnusable("a%2Fb.png")
        assertUnusable("a%5Cb.png")
    }

    @Test
    fun `an encoded climb cannot bypass the ascend budget`() {
        assertUnusable("%2e%2e/x.png")
        assertUnusable("%2E%2E/%2E%2E/x.png")
    }

    @Test
    fun `an encoded nul is refused`() {
        assertUnusable("a%00.png")
    }

    @Test
    fun `backslashes are folded before the traversal check sees them`() {
        assertEquals(listOf("images", "pic.png"), local("images\\pic.png").path.segments)

        // Left literal this would be one innocent-looking segment named `..\..\..\secret.png`,
        // which no `..` check would ever fire on. Folded, the climb is counted and Phase 2
        // gets to refuse it against the document's actual depth in the tree.
        assertEquals(
            LocalPath(ascend = 3, segments = listOf("secret.png")),
            local("..\\..\\..\\secret.png").path,
        )
        assertUnusable("/..\\..\\secret.png")
    }

    @Test
    fun `a windows drive letter is not a scheme`() {
        assertUnusable("C:\\images\\pic.png")
        assertUnusable("c:/images/pic.png")
    }

    @Test
    fun `remote schemes are left to the privacy gate`() {
        assertEquals(ImageTarget.Remote, ImagePath.classify("http://example.com/a.png"))
        assertEquals(ImageTarget.Remote, ImagePath.classify("HTTPS://example.com/a.png"))
    }

    @Test
    fun `a protocol-relative url is refused rather than treated as a path`() {
        // Not remote by `isRemoteImage` -- it has no scheme -- and collapsing the empty
        // segments would turn a network reference into a lookup for the file `cdn/a.png`.
        assertUnusable("//cdn.example.com/a.png")
    }

    @Test
    fun `schemes that already resolve are handed over untouched`() {
        listOf(
            "data:image/png;base64,AAAA",
            "content://com.example/doc/1",
            "file:///storage/emulated/0/a.png",
            "android.resource://com.example/1",
        ).forEach { assertEquals(it, ImageTarget.Direct, ImagePath.classify(it)) }
    }

    @Test
    fun `a colon inside a path is not a scheme`() {
        assertEquals(listOf("img", "a:b.png"), local("img/a:b.png").path.segments)
    }

    @Test
    fun `nothing to point at is refused`() {
        assertUnusable("")
        assertUnusable("   ")
        assertEquals(ImageTarget.Unusable, ImagePath.classify(null))
        // A folder is not an image.
        assertUnusable("./")
        assertUnusable("/")
        assertUnusable("?v=2")
    }

    @Test
    fun `oversized paths are refused`() {
        assertUnusable((1..ImagePath.MAX_SEGMENTS + 1).joinToString("/") { "d$it" } + "/a.png")
        assertUnusable("../".repeat(ImagePath.MAX_ASCEND + 1) + "a.png")
        assertUnusable("a".repeat(ImagePath.MAX_SEGMENT_CHARS + 1) + ".png")
    }

    @Test
    fun `surrounding whitespace does not change the answer`() {
        assertEquals(local("images/pic.png"), local("  images/pic.png  "))
    }
}
