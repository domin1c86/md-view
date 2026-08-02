package com.mdview.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCodecTest {

    private val entry = LibraryEntry(
        uri = "content://com.android.providers.downloads/document/42",
        displayName = "notes.md",
        title = "Release notes",
        excerpt = "Everything that changed since the last build.",
        lastOpened = 1_753_000_000_000L,
        isFavorite = true,
        canWrite = false,
        isTransient = true,
    )

    @Test
    fun `an entry survives a round trip`() {
        assertEquals(listOf(entry), LibraryCodec.decode(LibraryCodec.encode(listOf(entry))))
    }

    @Test
    fun `separators inside the text do not corrupt the row`() {
        // A heading really can contain a tab, and an excerpt spans lines by nature.
        // Unescaped, either one shifts every later field along by one.
        val awkward = entry.copy(
            title = "Tab\there",
            excerpt = "Line one\nline two\r\nand a backslash \\ plus \\t literal",
        )

        val decoded = LibraryCodec.decode(LibraryCodec.encode(listOf(awkward)))

        assertEquals(listOf(awkward), decoded)
    }

    @Test
    fun `order is preserved across many entries`() {
        val entries = (1..20).map {
            entry.copy(uri = "content://doc/$it", lastOpened = it.toLong())
        }

        assertEquals(entries, LibraryCodec.decode(LibraryCodec.encode(entries)))
    }

    @Test
    fun `an empty library round trips`() {
        assertEquals(emptyList<LibraryEntry>(), LibraryCodec.decode(LibraryCodec.encode(emptyList())))
    }

    @Test
    fun `a blank title comes back as null rather than empty`() {
        val untitled = entry.copy(title = null)

        assertEquals(null, LibraryCodec.decode(LibraryCodec.encode(listOf(untitled))).single().title)
    }

    @Test
    fun `a file from a newer version is discarded rather than misread`() {
        val future = LibraryCodec.encode(listOf(entry)).replaceFirst("v2", "v3")

        assertTrue(LibraryCodec.decode(future).isEmpty())
    }

    @Test
    fun `a file with no version header is discarded`() {
        assertTrue(LibraryCodec.decode("content://doc/1\tnotes.md\t\t\t0\t0\t1\t0\n").isEmpty())
    }

    @Test
    fun `a v1 file written before folders existed still reads`() {
        // The upgrade path. Refusing eight-field rows would silently empty the recents
        // list of everyone who had used the app before folders shipped.
        val v1 = "v1\ncontent://doc/1\tnotes.md\tRelease notes\tAn excerpt\t42\t1\t0\t1\n"

        val decoded = LibraryCodec.decode(v1).single()

        assertEquals("content://doc/1", decoded.uri)
        assertEquals("Release notes", decoded.title)
        assertEquals(42L, decoded.lastOpened)
        assertTrue(decoded.isFavorite)
        assertNull(decoded.folderId)
    }

    @Test
    fun `a v1 row is rewritten as v2`() {
        val v1 = "v1\ncontent://doc/1\tnotes.md\tRelease notes\tAn excerpt\t42\t1\t0\t1\n"

        val rewritten = LibraryCodec.encode(LibraryCodec.decode(v1))

        assertTrue(rewritten.startsWith("v2\n"))
        assertEquals(LibraryCodec.decode(v1), LibraryCodec.decode(rewritten))
    }

    @Test
    fun `a v1 row carrying an extra field is dropped rather than misread as v2`() {
        // Nine fields under a v1 header is not a filed document -- it is a corrupt row,
        // and reading its ninth field as a folder id would invent a folder.
        val text = "v1\ncontent://doc/1\tnotes.md\t\t\t0\t0\t1\t0\tf1\n"

        assertTrue(LibraryCodec.decode(text).isEmpty())
    }

    @Test
    fun `a filed document survives a round trip`() {
        val filed = entry.copy(folderId = "4f3c-90ab")

        assertEquals("4f3c-90ab", LibraryCodec.decode(LibraryCodec.encode(listOf(filed))).single().folderId)
    }

    @Test
    fun `an empty folder field comes back as null rather than blank`() {
        assertNull(LibraryCodec.decode(LibraryCodec.encode(listOf(entry))).single().folderId)
    }

    @Test
    fun `a truncated row is dropped but its neighbours survive`() {
        val rows = LibraryCodec.encode(listOf(entry, entry.copy(uri = "content://doc/2")))
            .lineSequence()
            .filter { it.isNotBlank() }
            .toList()
        val text = (rows.take(2) + "content://doc/broken\tonly-two-fields" + rows.drop(2))
            .joinToString("\n")

        val decoded = LibraryCodec.decode(text)

        assertEquals(2, decoded.size)
        assertTrue(decoded.none { it.uri == "content://doc/broken" })
    }

    @Test
    fun `a row with an unparseable timestamp is dropped`() {
        val text = "v1\ncontent://doc/1\tnotes.md\t\t\tnot-a-number\t0\t1\t0\n"

        assertTrue(LibraryCodec.decode(text).isEmpty())
    }

    @Test
    fun `a row with no uri is dropped`() {
        val text = "v1\n\tnotes.md\t\t\t0\t0\t1\t0\n"

        assertTrue(LibraryCodec.decode(text).isEmpty())
    }

    @Test
    fun `trailing blank lines do not become empty entries`() {
        val text = LibraryCodec.encode(listOf(entry)) + "\n\n\n"

        assertEquals(1, LibraryCodec.decode(text).size)
    }
}
