package com.mdview.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun store(dispatcher: TestDispatcher) = LibraryStore(folder.root, dispatcher)

    private fun entry(id: Int, opened: Long = id.toLong(), favorite: Boolean = false) =
        LibraryEntry(
            uri = "content://doc/$id",
            displayName = "doc$id.md",
            title = "Document $id",
            excerpt = "Body of document $id",
            lastOpened = opened,
            isFavorite = favorite,
        )

    @Test
    fun `an empty store loads as Empty rather than Loading`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))

        store.load()

        assertEquals(LibraryState.Empty, store.state.value)
    }

    @Test
    fun `a recorded document comes back after a reload`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        store(dispatcher).record(entry(1))

        val reopened = store(dispatcher)
        reopened.load()

        assertEquals(listOf(entry(1)), reopened.snapshot())
    }

    @Test
    fun `recording an open document again moves it to the top`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        store.record(entry(1, opened = 100))
        store.record(entry(2, opened = 200))

        store.record(entry(1, opened = 300))

        assertEquals(listOf("content://doc/1", "content://doc/2"), store.snapshot().map { it.uri })
    }

    @Test
    fun `reopening a document does not clear its star`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        store.record(entry(1))
        store.setFavorite("content://doc/1", true)

        // The ViewModel builds a fresh entry from the file on every open and knows
        // nothing about favourites, so the store has to preserve the flag itself.
        store.record(entry(1, opened = 999))

        assertTrue(store.snapshot().single().isFavorite)
    }

    @Test
    fun `a summary update leaves the position alone`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        store.record(entry(1, opened = 100))
        store.record(entry(2, opened = 200))

        store.updateSummary("content://doc/1", "Renamed", "New excerpt")

        assertEquals(listOf("content://doc/2", "content://doc/1"), store.snapshot().map { it.uri })
        val updated = store.snapshot().first { it.uri == "content://doc/1" }
        assertEquals("Renamed", updated.title)
        assertEquals("New excerpt", updated.excerpt)
    }

    @Test
    fun `removing returns the row so its grant can be released`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        store.record(entry(1))

        val removed = store.remove("content://doc/1")

        assertEquals("content://doc/1", removed?.uri)
        assertEquals(LibraryState.Empty, store.state.value)
        assertNull(store.remove("content://doc/1"))
    }

    @Test
    fun `the list is capped`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))

        repeat(LibraryStore.MAX_ENTRIES + 10) { store.record(entry(it, opened = it.toLong())) }

        assertEquals(LibraryStore.MAX_ENTRIES, store.snapshot().size)
    }

    @Test
    fun `a favourite is never evicted to make room`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        store.record(entry(0, opened = 0))
        store.setFavorite("content://doc/0", true)

        // Everything else is newer, so a plain LRU would drop the starred one first --
        // which is the single row the user said they wanted kept.
        repeat(LibraryStore.MAX_ENTRIES + 10) { store.record(entry(it + 1, opened = it + 1L)) }

        assertTrue(store.snapshot().any { it.uri == "content://doc/0" })
        assertEquals(LibraryStore.MAX_ENTRIES, store.snapshot().size)
    }

    @Test
    fun `concurrent writes do not lose each other`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        store.record(entry(1))
        store.record(entry(2))

        // Starring one document while another is opened: without the mutex both
        // rewrite the whole file from their own copy and one of the two vanishes.
        listOf(
            async { store.setFavorite("content://doc/1", true) },
            async { store.record(entry(3, opened = 300)) },
            async { store.setFavorite("content://doc/2", true) },
        ).awaitAll()

        val entries = store.snapshot()
        assertEquals(3, entries.size)
        assertTrue(entries.first { it.uri == "content://doc/1" }.isFavorite)
        assertTrue(entries.first { it.uri == "content://doc/2" }.isFavorite)
    }

    @Test
    fun `a write before the first load does not discard what is on disk`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        store(dispatcher).record(entry(1))

        // An ACTION_VIEW intent opens a document immediately, which can beat the
        // startup load(). Starting from an empty list would rewrite the file with
        // one row and drop everything else.
        val fresh = store(dispatcher)
        fresh.record(entry(2, opened = 500))

        assertEquals(2, fresh.snapshot().size)
    }

    @Test
    fun `a corrupt file loads as empty instead of throwing`() = runTest {
        folder.newFile("entries.tsv").writeText("not a library at all")
        val store = store(StandardTestDispatcher(testScheduler))

        store.load()

        assertEquals(LibraryState.Empty, store.state.value)
    }

    // -- Folders. In-app labels only: nothing below creates a directory anywhere. --

    @Test
    fun `a folder comes back after a reload`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val created = store(dispatcher).createFolder("Work")

        val reopened = store(dispatcher)
        reopened.load()

        assertEquals(listOf(created), reopened.folders.value)
    }

    @Test
    fun `a folder name is trimmed`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))

        assertEquals("Work", store.createFolder("  Work  ")?.name)
    }

    @Test
    fun `a blank or over-long name is refused`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))

        assertNull(store.createFolder("   "))
        assertNull(store.createFolder("x".repeat(FolderNames.MAX_LENGTH + 1)))
        assertTrue(store.folders.value.isEmpty())
    }

    @Test
    fun `a duplicate name is refused whatever its case`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        store.createFolder("Work")

        // Two chips reading "Work" and "work" are indistinguishable on the strip, and
        // filing into the wrong one would be silent.
        assertNull(store.createFolder("work"))
        assertEquals(1, store.folders.value.size)
    }

    @Test
    fun `the folder catalogue is capped`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))

        repeat(FolderNames.MAX_FOLDERS + 5) { store.createFolder("Folder $it") }

        assertEquals(FolderNames.MAX_FOLDERS, store.folders.value.size)
    }

    @Test
    fun `renaming keeps everything filed in it`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        val work = store.createFolder("Work")!!
        store.record(entry(1))
        store.setFolder("content://doc/1", work.id)

        assertTrue(store.renameFolder(work.id, "Projects"))

        assertEquals("Projects", store.folders.value.single().name)
        // The id is what the entry references, which is the whole reason it exists.
        assertEquals(work.id, store.snapshot().single().folderId)
    }

    @Test
    fun `renaming to a name it already has is allowed`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        val work = store.createFolder("Work")!!

        // Opening the rename dialog and pressing Rename without editing must not be
        // rejected as a duplicate of itself.
        assertTrue(store.renameFolder(work.id, "Work"))
    }

    @Test
    fun `renaming onto another folder's name is refused`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        val work = store.createFolder("Work")!!
        store.createFolder("Recipes")

        assertFalse(store.renameFolder(work.id, "Recipes"))
        assertEquals("Work", store.folders.value.first { it.id == work.id }.name)
    }

    @Test
    fun `deleting a folder unfiles its documents rather than removing them`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        val work = store.createFolder("Work")!!
        store.record(entry(1))
        store.record(entry(2))
        store.setFolder("content://doc/1", work.id)

        store.deleteFolder(work.id)

        // Deleting a label the user made must not cost them the documents wearing it --
        // and there was never a directory to delete either way.
        assertEquals(2, store.snapshot().size)
        assertTrue(store.snapshot().all { it.folderId == null })
        assertTrue(store.folders.value.isEmpty())
    }

    @Test
    fun `filing into a folder that does not exist is ignored`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        store.record(entry(1))

        store.setFolder("content://doc/1", "no-such-folder")

        assertNull(store.snapshot().single().folderId)
    }

    @Test
    fun `reopening a document does not unfile it`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        val work = store.createFolder("Work")!!
        store.record(entry(1))
        store.setFolder("content://doc/1", work.id)

        // The ViewModel builds a fresh entry on every open and knows nothing about
        // folders, exactly as it knows nothing about favourites.
        store.record(entry(1, opened = 999))

        assertEquals(work.id, store.snapshot().single().folderId)
    }

    @Test
    fun `a filed document is never evicted to make room`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        val work = store.createFolder("Work")!!
        store.record(entry(0, opened = 0))
        store.setFolder("content://doc/0", work.id)

        // Filing is as deliberate as starring. A folder that quietly empties itself at
        // fifty entries is worse than no folder at all.
        repeat(LibraryStore.MAX_ENTRIES + 10) { store.record(entry(it + 1, opened = it + 1L)) }

        assertTrue(store.snapshot().any { it.uri == "content://doc/0" })
        assertEquals(LibraryStore.MAX_ENTRIES, store.snapshot().size)
    }

    @Test
    fun `a document pointing at a folder that is gone loads unfiled`() = runTest {
        // Self-healing against a half-written pair of files: entries.tsv landed, and
        // folders.tsv did not.
        val orphan = entry(1).copy(folderId = "vanished")
        folder.newFile("entries.tsv").writeText(LibraryCodec.encode(listOf(orphan)))
        val store = store(StandardTestDispatcher(testScheduler))

        store.load()

        assertNull(store.snapshot().single().folderId)
    }

    @Test
    fun `a corrupt folders file leaves the documents alone`() = runTest {
        folder.newFile("entries.tsv").writeText(LibraryCodec.encode(listOf(entry(1))))
        folder.newFile("folders.tsv").writeText("not a folder list at all")
        val store = store(StandardTestDispatcher(testScheduler))

        store.load()

        assertEquals(1, store.snapshot().size)
        assertTrue(store.folders.value.isEmpty())
    }

    @Test
    fun `creating a folder before the first load does not discard what is on disk`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        store(dispatcher).createFolder("Work")

        val fresh = store(dispatcher)
        fresh.createFolder("Recipes")

        assertEquals(listOf("Work", "Recipes"), fresh.folders.value.map { it.name })
    }
}
