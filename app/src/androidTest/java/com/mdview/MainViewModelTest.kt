package com.mdview

import android.net.Uri
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mdview.data.DocumentSource
import com.mdview.data.DraftStore
import com.mdview.data.LibraryState
import com.mdview.data.LibraryStore
import com.mdview.data.LoadedDocument
import com.mdview.data.PersistedAccess
import com.mdview.markdown.DocumentCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException

/**
 * Covers open, save, dirty-tracking and navigation restore -- the logic where a user's
 * unsaved work is either kept or lost. Instrumented rather than a JVM test because [Uri]
 * has no working implementation off-device, and every one of these paths is keyed by one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var directory: File
    private lateinit var documents: FakeDocuments
    private lateinit var drafts: DraftStore
    private lateinit var library: LibraryStore

    private val uri: Uri = Uri.parse("content://test/notes.md")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        directory = File(cache, "vm-test-${System.nanoTime()}")
        documents = FakeDocuments()
        drafts = DraftStore(File(directory, "drafts"), dispatcher)
        library = LibraryStore(File(directory, "library"), dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        directory.deleteRecursively()
    }

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        MainViewModel(documents, drafts, library, savedState, parsing = dispatcher)

    /** A SavedStateHandle as it comes back after a process death on the document screen. */
    private fun onDocument(uri: Uri? = null) = SavedStateHandle(
        buildMap {
            put("destination", Destination.Document.name)
            uri?.let { put("open_document_uri", it.toString()) }
        }
    )

    /**
     * Types into the buffer and makes sure `snapshotFlow` has heard about it.
     *
     * **The nested snapshot is the whole point, not tidiness.**
     * `TextFieldState.setTextAndPlaceCursorAtEnd` writes into the *current* snapshot, which
     * outside an explicit one is the global snapshot -- so the edit sits there as a pending
     * change until somebody calls `sendApplyNotifications`. In this process somebody else
     * does: any Compose UI suite that ran earlier has started
     * `androidx.compose.ui.platform.GlobalSnapshotManager`, which watches for global writes
     * and calls `sendApplyNotifications` itself from the real main thread, for the lifetime
     * of the process.
     *
     * Two threads then race for one pending change. When the main thread wins, this
     * test's own call finds nothing pending and returns immediately, the notification is
     * delivered over there instead, and it can land *after* `advanceUntilIdle` has already
     * drained the scheduler -- so the collector in `MainViewModel` runs too late and the
     * dirty and draft assertions read stale state. That is the whole story behind "fails
     * one random draft case per full run, passes 25/25 alone".
     *
     * `withMutableSnapshot` ends it: `MutableSnapshot.apply()` invokes the apply observers
     * **synchronously on this thread**, so the flow has been signalled before this function
     * returns and there is no pending global change left for anyone to steal.
     *
     * The trailing call stays as belt and braces -- if any part of the edit path ever
     * writes outside the nested snapshot, that write is still notified exactly as before.
     * It cannot bring the race back, because the synchronous apply has already happened.
     */
    private fun MainViewModel.type(text: String) {
        Snapshot.withMutableSnapshot {
            textState.setTextAndPlaceCursorAtEnd(text)
        }
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun openingReadsTheDocumentAndStartsClean() = runTest(dispatcher) {
        documents.put(uri, "# On disk\n")
        val model = viewModel()

        model.open(uri)
        advanceUntilIdle()

        assertEquals("# On disk\n", model.textState.text.toString())
        assertEquals(uri, model.uiState.value.uri)
        assertFalse(model.uiState.value.isDirty)
    }

    @Test
    fun openingMovesToTheDocumentScreen() = runTest(dispatcher) {
        documents.put(uri, "text")
        val model = viewModel()
        assertEquals(Destination.Dashboard, model.uiState.value.destination)

        model.open(uri)
        advanceUntilIdle()

        assertEquals(Destination.Document, model.uiState.value.destination)
    }

    @Test
    fun editingMarksTheDocumentDirty() = runTest(dispatcher) {
        documents.put(uri, "original")
        val model = viewModel()
        model.open(uri)
        advanceUntilIdle()

        model.type("original plus more")
        advanceUntilIdle()

        assertTrue(model.uiState.value.isDirty)
    }

    @Test
    fun anEditIsAutosavedAsADraft() = runTest(dispatcher) {
        documents.put(uri, "original")
        val model = viewModel()
        model.open(uri)
        advanceUntilIdle()

        model.type("half-finished sentence")
        advanceUntilIdle()

        assertEquals("half-finished sentence", drafts.load(DraftStore.keyFor(uri.toString())))
    }

    @Test
    fun unsavedWorkComesBackAfterTheProcessIsKilled() = runTest(dispatcher) {
        documents.put(uri, "original")
        val first = viewModel()
        first.open(uri)
        advanceUntilIdle()
        first.type("work in progress")
        advanceUntilIdle()

        // A brand new ViewModel over the same storage is what a restart looks like.
        val second = viewModel()
        second.open(uri)
        advanceUntilIdle()

        assertEquals("work in progress", second.textState.text.toString())
        assertTrue(second.uiState.value.isDirty)
        assertEquals(R.string.draft_restored, second.uiState.value.message?.resId)
    }

    @Test
    fun sittingOnTheDashboardDoesNotDeleteAnUnsavedScratchDocument() = runTest(dispatcher) {
        drafts.save(DraftStore.UNTITLED_KEY, "notes I never saved anywhere")
        advanceUntilIdle()

        // snapshotFlow emits as soon as it is collected, so the autosave debounce fires
        // with an empty buffer shortly after launch. Ungated, it would decide the empty
        // text matches the equally-empty savedText and clear the draft -- destroying the
        // previous session's work while the user looks at the dashboard.
        viewModel()
        advanceUntilIdle()

        assertEquals("notes I never saved anywhere", drafts.load(DraftStore.UNTITLED_KEY))
    }

    @Test
    fun aScratchDocumentIsOfferedOnTheDashboardRatherThanForcedOpen() = runTest(dispatcher) {
        drafts.save(DraftStore.UNTITLED_KEY, "half a thought")
        advanceUntilIdle()

        val model = viewModel()
        advanceUntilIdle()

        assertEquals(Destination.Dashboard, model.uiState.value.destination)
        assertTrue(model.hasUntitledDraft.value)
        assertEquals("", model.textState.text.toString())
    }

    @Test
    fun theOfferedScratchDocumentOpensOnRequest() = runTest(dispatcher) {
        drafts.save(DraftStore.UNTITLED_KEY, "half a thought")
        advanceUntilIdle()
        val model = viewModel()
        advanceUntilIdle()

        model.openUntitledDraft()
        advanceUntilIdle()

        assertEquals("half a thought", model.textState.text.toString())
        assertEquals(Destination.Document, model.uiState.value.destination)
        assertTrue(model.uiState.value.isDirty)
    }

    @Test
    fun deathOnTheDocumentScreenRestoresTheOpenFile() = runTest(dispatcher) {
        documents.put(uri, "on disk")

        val model = viewModel(onDocument(uri))
        advanceUntilIdle()

        assertEquals(Destination.Document, model.uiState.value.destination)
        assertEquals("on disk", model.textState.text.toString())
    }

    @Test
    fun deathOnTheDocumentScreenRestoresAnUntitledDocument() = runTest(dispatcher) {
        drafts.save(DraftStore.UNTITLED_KEY, "unsaved scratch")
        advanceUntilIdle()

        val model = viewModel(onDocument())
        advanceUntilIdle()

        assertEquals(Destination.Document, model.uiState.value.destination)
        assertEquals("unsaved scratch", model.textState.text.toString())
    }

    @Test
    fun deathWithNothingToRestoreFallsBackToTheDashboard() = runTest(dispatcher) {
        // Killed after navigating to the editor but before typing anything.
        val model = viewModel(onDocument())
        advanceUntilIdle()

        assertEquals(Destination.Dashboard, model.uiState.value.destination)
    }

    @Test
    fun aColdLaunchOpensNothing() = runTest(dispatcher) {
        documents.put(uri, "on disk")

        val model = viewModel()
        advanceUntilIdle()

        assertEquals(Destination.Dashboard, model.uiState.value.destination)
        assertNull(model.uiState.value.uri)
        assertEquals("", model.textState.text.toString())
    }

    @Test
    fun discardingARestoredDraftGoesBackToTheFileOnDisk() = runTest(dispatcher) {
        documents.put(uri, "original")
        val first = viewModel()
        first.open(uri)
        advanceUntilIdle()
        first.type("work in progress")
        advanceUntilIdle()

        val second = viewModel()
        second.open(uri)
        advanceUntilIdle()
        val message = second.uiState.value.message
        assertNotNull(message)
        second.onMessageAction(message!!.id)
        advanceUntilIdle()

        assertEquals("original", second.textState.text.toString())
        assertFalse(second.uiState.value.isDirty)
        assertNull(drafts.load(DraftStore.keyFor(uri.toString())))
    }

    @Test
    fun savingWritesTheTextAndDropsTheDraft() = runTest(dispatcher) {
        documents.put(uri, "original")
        val model = viewModel()
        model.open(uri)
        advanceUntilIdle()
        model.type("edited")
        advanceUntilIdle()

        model.save()
        advanceUntilIdle()

        assertEquals("edited", documents.textOf(uri))
        assertFalse(model.uiState.value.isDirty)
        assertNull(drafts.load(DraftStore.keyFor(uri.toString())))
    }

    @Test
    fun savingPutsBackTheLineEndingsTheFileArrivedWith() = runTest(dispatcher) {
        documents.put(uri, "one\ntwo\n", DocumentCodec.LineEndings.CrLf, hadBom = true)
        val model = viewModel()
        model.open(uri)
        advanceUntilIdle()
        model.type("one\ntwo\nthree\n")
        advanceUntilIdle()

        model.save()
        advanceUntilIdle()

        val written = documents.stored.getValue(uri.toString())
        assertEquals(DocumentCodec.LineEndings.CrLf, written.lineEndings)
        assertTrue(written.hadBom)
    }

    @Test
    fun startingANewDocumentClearsEverything() = runTest(dispatcher) {
        documents.put(uri, "original")
        val model = viewModel()
        model.open(uri)
        advanceUntilIdle()
        model.type("edited")
        advanceUntilIdle()

        model.newDocument()
        advanceUntilIdle()

        assertEquals("", model.textState.text.toString())
        assertNull(model.uiState.value.uri)
        assertNull(model.uiState.value.fileName)
        assertFalse(model.uiState.value.isDirty)
        assertEquals(Mode.Edit, model.uiState.value.mode)
        assertEquals(Destination.Document, model.uiState.value.destination)
    }

    @Test
    fun aMissingFileReportsSomethingAUserCanRead() = runTest(dispatcher) {
        val model = viewModel()

        model.open(Uri.parse("content://test/gone.md"))
        advanceUntilIdle()

        assertEquals(R.string.error_open_missing, model.uiState.value.message?.resId)
        assertFalse(model.uiState.value.isBusy)
    }

    @Test
    fun aFileThatWillNotOpenLeavesTheUserOnTheDashboard() = runTest(dispatcher) {
        val model = viewModel()

        model.open(Uri.parse("content://test/gone.md"))
        advanceUntilIdle()

        // Nothing to show, so stranding the user on a blank document screen would be worse.
        assertEquals(Destination.Dashboard, model.uiState.value.destination)
    }

    @Test
    fun aFailedSaveLeavesTheDocumentDirty() = runTest(dispatcher) {
        documents.put(uri, "original")
        val model = viewModel()
        model.open(uri)
        advanceUntilIdle()
        model.type("edited")
        advanceUntilIdle()
        documents.writeFailure = FileNotFoundException("gone")

        model.save()
        advanceUntilIdle()

        assertTrue(model.uiState.value.isDirty)
        assertEquals(R.string.error_save_missing, model.uiState.value.message?.resId)
        // The draft is the only surviving copy, so it had better still be there.
        assertEquals("edited", drafts.load(DraftStore.keyFor(uri.toString())))
    }

    @Test
    fun openingADocumentPutsItInTheLibrary() = runTest(dispatcher) {
        documents.put(uri, "---\ntitle: Meeting notes\n---\n\nWhat we agreed.\n")
        val model = viewModel()

        model.open(uri)
        advanceUntilIdle()

        val entry = (library.state.value as LibraryState.Content).entries.single()
        assertEquals(uri.toString(), entry.uri)
        assertEquals("notes.md", entry.displayName)
        assertEquals("Meeting notes", entry.title)
        assertEquals("What we agreed.", entry.excerpt)
    }

    @Test
    fun savingRefreshesTheCardExcerpt() = runTest(dispatcher) {
        documents.put(uri, "# Before\n\nOld body.\n")
        val model = viewModel()
        model.open(uri)
        advanceUntilIdle()

        model.type("# After\n\nNew body.\n")
        advanceUntilIdle()
        model.save()
        advanceUntilIdle()

        val entry = (library.state.value as LibraryState.Content).entries.single()
        assertEquals("After", entry.title)
        assertEquals("New body.", entry.excerpt)
    }

    @Test
    fun aReadOnlyDocumentReportsThatSaveWillNotWork() = runTest(dispatcher) {
        documents.put(uri, "text")
        documents.access = PersistedAccess.ReadOnly
        val model = viewModel()

        model.open(uri)
        advanceUntilIdle()

        assertFalse(model.uiState.value.canWrite)
        assertFalse((library.state.value as LibraryState.Content).entries.single().canWrite)
    }

    @Test
    fun aDocumentFromAnIntentIsMarkedAsNotPersisted() = runTest(dispatcher) {
        documents.put(uri, "text")
        documents.access = PersistedAccess.None
        val model = viewModel()

        model.open(uri)
        advanceUntilIdle()

        assertTrue((library.state.value as LibraryState.Content).entries.single().isTransient)
    }

    @Test
    fun forgettingADocumentDropsItAndHandsBackItsGrant() = runTest(dispatcher) {
        documents.put(uri, "text")
        val model = viewModel()
        model.open(uri)
        advanceUntilIdle()

        model.forget(uri.toString())
        advanceUntilIdle()

        assertEquals(LibraryState.Empty, library.state.value)
        assertEquals(setOf(uri.toString()), documents.released)
    }

    @Test
    fun reopeningTheSameIntentNavigatesWithoutDiscardingEdits() = runTest(dispatcher) {
        documents.put(uri, "on disk")
        val model = viewModel()
        model.openFromIntent(uri)
        advanceUntilIdle()
        model.type("unsaved edit")
        advanceUntilIdle()
        model.goToDashboard()
        advanceUntilIdle()

        // Tapping the same file in a file manager again: it must navigate back, but
        // re-reading would throw away what was typed.
        model.openFromIntent(uri)
        advanceUntilIdle()

        assertEquals(Destination.Document, model.uiState.value.destination)
        assertEquals("unsaved edit", model.textState.text.toString())
    }

    private class FakeDocuments : DocumentSource {
        val stored = mutableMapOf<String, LoadedDocument>()
        val released = mutableSetOf<String>()
        var writeFailure: Throwable? = null
        var access: PersistedAccess = PersistedAccess.ReadWrite

        fun put(
            uri: Uri,
            text: String,
            lineEndings: DocumentCodec.LineEndings = DocumentCodec.LineEndings.Lf,
            hadBom: Boolean = false,
        ) {
            stored[uri.toString()] = LoadedDocument(text, lineEndings, hadBom)
        }

        fun textOf(uri: Uri): String? = stored[uri.toString()]?.text

        override suspend fun read(uri: Uri): LoadedDocument =
            stored[uri.toString()] ?: throw FileNotFoundException(uri.toString())

        override suspend fun write(uri: Uri, document: LoadedDocument) {
            writeFailure?.let { throw it }
            stored[uri.toString()] = document
        }

        override suspend fun displayName(uri: Uri): String? = uri.lastPathSegment

        override fun persistAccess(uri: Uri): PersistedAccess = access

        override fun releaseAccess(uri: Uri, access: PersistedAccess) {
            released += uri.toString()
        }

        override fun persistedUris(): Set<String> = stored.keys
    }
}
