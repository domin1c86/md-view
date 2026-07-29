package com.mdview.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The on-disk form of the granted image folders. */
class FolderGrantCodecTest {

    private val grant = FolderGrant(
        treeUri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments",
        displayName = "Documents",
        grantedAt = 1_753_000_000_000L,
    )

    @Test
    fun `a grant round trips`() {
        assertEquals(listOf(grant), FolderGrantCodec.decode(FolderGrantCodec.encode(listOf(grant))))
    }

    @Test
    fun `an empty list round trips`() {
        assertEquals(emptyList<FolderGrant>(), FolderGrantCodec.decode(FolderGrantCodec.encode(emptyList())))
    }

    @Test
    fun `order is preserved`() {
        val grants = listOf(grant, grant.copy(treeUri = "content://x/tree/b", grantedAt = 1L))
        assertEquals(grants, FolderGrantCodec.decode(FolderGrantCodec.encode(grants)))
    }

    @Test
    fun `a display name containing a tab does not shift the later fields`() {
        // Provider display names are arbitrary text, which is why every field is escaped.
        val awkward = grant.copy(displayName = "My\tNotes\nand\rmore\\stuff")
        assertEquals(listOf(awkward), FolderGrantCodec.decode(FolderGrantCodec.encode(listOf(awkward))))
    }

    @Test
    fun `a row with the wrong field count is dropped`() {
        assertEquals(emptyList<FolderGrant>(), FolderGrantCodec.decode("v1\ncontent://x\tOnly two"))
    }

    @Test
    fun `a row with a non-numeric timestamp is dropped`() {
        assertEquals(emptyList<FolderGrant>(), FolderGrantCodec.decode("v1\ncontent://x\tName\tsoon"))
    }

    @Test
    fun `a row with no uri is dropped`() {
        assertEquals(emptyList<FolderGrant>(), FolderGrantCodec.decode("v1\n\tName\t1"))
    }

    @Test
    fun `an unknown version reads as nothing rather than as garbage`() {
        assertEquals(emptyList<FolderGrant>(), FolderGrantCodec.decode("v2\ncontent://x\tName\t1"))
        assertEquals(emptyList<FolderGrant>(), FolderGrantCodec.decode(""))
    }

    @Test
    fun `one bad row does not take the good ones with it`() {
        val text = FolderGrantCodec.encode(listOf(grant)).trimEnd() + "\nbroken\n"
        assertEquals(listOf(grant), FolderGrantCodec.decode(text))
    }

    @Test
    fun `the encoded form is one line per grant under a version header`() {
        val lines = FolderGrantCodec.encode(listOf(grant, grant.copy(treeUri = "content://x/tree/b")))
            .lines()
            .filter { it.isNotBlank() }
        assertEquals(3, lines.size)
        assertTrue(lines.first() == "v1")
    }
}
