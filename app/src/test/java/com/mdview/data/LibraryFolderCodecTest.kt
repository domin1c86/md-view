package com.mdview.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFolderCodecTest {

    private val work = LibraryFolder(id = "4f3c-90ab", name = "Work", createdAt = 1_753_000_000_000L)

    @Test
    fun `a folder survives a round trip`() {
        assertEquals(
            listOf(work),
            LibraryFolderCodec.decode(LibraryFolderCodec.encode(listOf(work))),
        )
    }

    @Test
    fun `order is preserved, because it is the order of the chips`() {
        val folders = (1..5).map { work.copy(id = "id-$it", name = "Folder $it", createdAt = it.toLong()) }

        assertEquals(folders, LibraryFolderCodec.decode(LibraryFolderCodec.encode(folders)))
    }

    @Test
    fun `separators inside a name do not corrupt the row`() {
        // The name is typed by the user, and nothing stops them pasting a tab into it.
        val awkward = work.copy(name = "Tab\there and a backslash \\ plus \\t literal")

        assertEquals(
            listOf(awkward),
            LibraryFolderCodec.decode(LibraryFolderCodec.encode(listOf(awkward))),
        )
    }

    @Test
    fun `an empty catalogue round trips`() {
        assertEquals(
            emptyList<LibraryFolder>(),
            LibraryFolderCodec.decode(LibraryFolderCodec.encode(emptyList())),
        )
    }

    @Test
    fun `a file from a newer version is discarded rather than misread`() {
        val future = LibraryFolderCodec.encode(listOf(work)).replaceFirst("v1", "v2")

        assertTrue(LibraryFolderCodec.decode(future).isEmpty())
    }

    @Test
    fun `a file with no version header is discarded`() {
        assertTrue(LibraryFolderCodec.decode("4f3c\tWork\t0\n").isEmpty())
    }

    @Test
    fun `a truncated row is dropped but its neighbours survive`() {
        val text = "v1\n4f3c\tWork\t1\nbroken-row\n7a1b\tRecipes\t2\n"

        val decoded = LibraryFolderCodec.decode(text)

        assertEquals(listOf("Work", "Recipes"), decoded.map { it.name })
    }

    @Test
    fun `a row with an unparseable timestamp is dropped`() {
        assertTrue(LibraryFolderCodec.decode("v1\n4f3c\tWork\tnot-a-number\n").isEmpty())
    }

    @Test
    fun `a row with no id is dropped`() {
        // Nothing could ever be filed in it, since the entries reference the id.
        assertTrue(LibraryFolderCodec.decode("v1\n\tWork\t1\n").isEmpty())
    }

    @Test
    fun `a row with no name is dropped`() {
        // It would draw as an unlabelled chip nobody could identify.
        assertTrue(LibraryFolderCodec.decode("v1\n4f3c\t\t1\n").isEmpty())
    }

    @Test
    fun `trailing blank lines do not become empty folders`() {
        val text = LibraryFolderCodec.encode(listOf(work)) + "\n\n\n"

        assertEquals(1, LibraryFolderCodec.decode(text).size)
    }
}
