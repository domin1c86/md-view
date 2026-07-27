package com.mdview

import android.net.Uri
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mdview.data.DocumentSource
import com.mdview.data.DraftStore
import com.mdview.data.LoadedDocument
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
 * Covers open, save and dirty-tracking -- the logic where a user's unsaved work is
 * either kept or lost. Instrumented rather than a JVM test because [Uri] has no working
 * implementation off-device, and every one of these paths is keyed by one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var directory: File
    private lateinit var documents: FakeDocuments
    private lateinit var drafts: DraftStore

    private val uri: Uri = Uri.parse("content://test/notes.md")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        directory = File(cache, "draft-test-${System.nanoTime()}")
        documents = FakeDocuments()
        drafts = DraftStore(directory, dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        directory.deleteRecursively()
    }

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        MainViewModel(documents, drafts, savedState)

    /** Mutating a [androidx.compose.foundation.text.input.TextFieldState] only reaches
     *  `snapshotFlow` once the global snapshot has been applied. */
    private fun MainViewModel.type(text: String) {
        textState.setTextAndPlaceCursorAtEnd(text)
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
    }

    @Test
    fun aScratchDocumentIsRecoveredWithNoFileToReopen() = runTest(dispatcher) {
        val first = viewModel()
        advanceUntilIdle()
        first.type("notes I never saved anywhere")
        advanceUntilIdle()

        val second = viewModel()
        advanceUntilIdle()

        assertEquals("notes I never saved anywhere", second.textState.text.toString())
        assertTrue(second.uiState.value.isDirty)
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

    private class FakeDocuments : DocumentSource {
        val stored = mutableMapOf<String, LoadedDocument>()
        var writeFailure: Throwable? = null

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

        override fun persistAccess(uri: Uri) = Unit
    }
}
