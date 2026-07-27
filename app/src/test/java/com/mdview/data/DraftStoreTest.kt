package com.mdview.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DraftStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val store by lazy { DraftStore(temporaryFolder.newFolder("drafts")) }

    @Test
    fun aSavedDraftComesBack() = runTest {
        store.save("doc", "unsaved work")

        assertEquals("unsaved work", store.load("doc"))
    }

    @Test
    fun anAbsentDraftIsNull() = runTest {
        assertNull(store.load("never-written"))
    }

    @Test
    fun savingAgainReplacesTheDraft() = runTest {
        store.save("doc", "first")
        store.save("doc", "second")

        assertEquals("second", store.load("doc"))
    }

    @Test
    fun clearingRemovesTheDraft() = runTest {
        store.save("doc", "unsaved work")
        store.clear("doc")

        assertNull(store.load("doc"))
    }

    @Test
    fun clearingSomethingThatWasNeverThereIsHarmless() = runTest {
        store.clear("never-written")
    }

    @Test
    fun draftsForDifferentDocumentsDoNotCollide() = runTest {
        store.save("one", "first document")
        store.save("two", "second document")

        assertEquals("first document", store.load("one"))
        assertEquals("second document", store.load("two"))
    }

    @Test
    fun theStoreCreatesItsDirectoryOnDemand() = runTest {
        val missing = DraftStore(temporaryFolder.newFolder("outer").resolve("not-yet-there"))
        missing.save("doc", "text")

        assertEquals("text", missing.load("doc"))
    }

    @Test
    fun keysAreFilenameSafeAndDistinct() {
        val key = DraftStore.keyFor("content://com.example/tree/primary%3ADocs/notes.md")

        assertEquals("", key.filterNot { it.isDigit() || it in 'a'..'f' })
        assertNotEquals(DraftStore.keyFor("content://com.example/other.md"), key)
    }

    @Test
    fun aDocumentWithNoUriGetsTheUntitledKey() {
        assertEquals(DraftStore.UNTITLED_KEY, DraftStore.keyFor(null))
    }
}
