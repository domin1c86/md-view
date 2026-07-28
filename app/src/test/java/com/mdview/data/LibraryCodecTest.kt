package com.mdview.data

import org.junit.Assert.assertEquals
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
        val future = LibraryCodec.encode(listOf(entry)).replaceFirst("v1", "v2")

        assertTrue(LibraryCodec.decode(future).isEmpty())
    }

    @Test
    fun `a file with no version header is discarded`() {
        assertTrue(LibraryCodec.decode("content://doc/1\tnotes.md\t\t\t0\t0\t1\t0\n").isEmpty())
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
