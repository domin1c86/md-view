package com.mdview.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
