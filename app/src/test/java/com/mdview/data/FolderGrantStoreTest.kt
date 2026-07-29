package com.mdview.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class FolderGrantStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun store(dispatcher: TestDispatcher) = FolderGrantStore(folder.root, dispatcher)

    private fun grant(id: Int, at: Long = id.toLong()) = FolderGrant(
        treeUri = "content://provider/tree/folder$id",
        displayName = "Folder $id",
        grantedAt = at,
    )

    @Test
    fun `a fresh store is empty`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))

        store.load()

        assertEquals(emptyList<FolderGrant>(), store.grants.value)
    }

    @Test
    fun `a granted folder comes back after a reload`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        store(dispatcher).add(grant(1))

        val reloaded = store(dispatcher)
        reloaded.load()

        assertEquals(listOf(grant(1)), reloaded.grants.value)
    }

    @Test
    fun `granting the same folder twice replaces rather than duplicates`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))

        store.add(grant(1, at = 10))
        store.add(grant(1, at = 20))

        assertEquals(listOf(grant(1, at = 20)), store.grants.value)
    }

    @Test
    fun `the newest grant is first`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))

        store.add(grant(1))
        store.add(grant(2))

        assertEquals(listOf(grant(2), grant(1)), store.grants.value)
    }

    @Test
    fun `removing returns the row so its permission can be released`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        store.add(grant(1))

        assertEquals(grant(1), store.remove(grant(1).treeUri))
        assertEquals(emptyList<FolderGrant>(), store.grants.value)
        assertNull(store.remove("content://provider/tree/never-granted"))
    }

    @Test
    fun `the cap evicts the oldest and hands it back`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))

        // The first one added is the oldest, so it is the one pushed out.
        repeat(FolderGrantStore.MAX_GRANTS) { index -> assertTrue(store.add(grant(index)).isEmpty()) }
        val evicted = store.add(grant(99))

        assertEquals(listOf(grant(0)), evicted)
        assertEquals(FolderGrantStore.MAX_GRANTS, store.grants.value.size)
        assertTrue(store.grants.value.none { it == grant(0) })
    }

    @Test
    fun `reconcile drops grants the system no longer honours`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        store.add(grant(1))
        store.add(grant(2))

        store.reconcile(setOf(grant(2).treeUri))

        assertEquals(listOf(grant(2)), store.grants.value)
    }

    @Test
    fun `reconcile against everything changes nothing`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        store.add(grant(1))
        store.add(grant(2))

        store.reconcile(setOf(grant(1).treeUri, grant(2).treeUri, "content://other/tree/x"))

        assertEquals(listOf(grant(2), grant(1)), store.grants.value)
    }

    @Test
    fun `a mutation before load does not clobber what is already on disk`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        store(dispatcher).add(grant(1))

        // No load() first: this is the ACTION_VIEW race, where an image asks for a folder
        // before the store has finished reading.
        val second = store(dispatcher)
        second.add(grant(2))

        assertEquals(listOf(grant(2), grant(1)), second.grants.value)
    }

    @Test
    fun `loading twice is harmless`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = store(dispatcher)
        store.add(grant(1))

        store.load()
        store.load()

        assertEquals(listOf(grant(1)), store.grants.value)
    }

    @Test
    fun `a corrupt file reads as no grants rather than crashing`() = runTest {
        File(folder.root, "grants.tsv").writeText("this is not a grant list")

        val store = store(StandardTestDispatcher(testScheduler))
        store.load()

        assertEquals(emptyList<FolderGrant>(), store.grants.value)
    }

    @Test
    fun `an over-full file on disk is capped on the way in`() = runTest {
        val overflowing = (1..FolderGrantStore.MAX_GRANTS + 5).map { grant(it) }
        File(folder.root, "grants.tsv").writeText(FolderGrantCodec.encode(overflowing))

        val store = store(StandardTestDispatcher(testScheduler))
        store.load()

        assertEquals(FolderGrantStore.MAX_GRANTS, store.grants.value.size)
        assertEquals(grant(FolderGrantStore.MAX_GRANTS + 5), store.grants.value.first())
    }

    @Test
    fun `snapshot matches the flow`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        store.add(grant(1))

        assertEquals(store.grants.value, store.snapshot())
    }
}
