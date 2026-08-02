package com.mdview

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mdview.data.LibraryCodec
import com.mdview.data.LibraryEntry
import com.mdview.ui.OverlayTags
import com.mdview.ui.dashboard.DashboardTags
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Folders, driven through the real UI.
 *
 * Instrumented rather than JVM because everything here is Compose: the chip strip, the two
 * dialogs and the back gesture. The store's own rules are covered on the JVM by
 * `LibraryStoreTest`, so this suite is about what the user can actually reach.
 *
 * The library is seeded on disk, as in [DashboardTest], because the only way into it is the
 * system file picker and that needs a human. Which also means the *import into a folder*
 * path cannot be tested here at all — it ends in `DocumentsUI` — and lives on the manual
 * checklist instead.
 */
@RunWith(AndroidJUnit4::class)
class LibraryFolderTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(object : ExternalResource() {
            override fun before() = TestStorage.wipe { context ->
                val library = File(context.filesDir, "library")
                library.mkdirs()
                File(library, "entries.tsv").writeText(LibraryCodec.encode(SEEDED))
            }
        })
        .around(rule)

    private fun label(resId: Int): String = rule.activity.getString(resId)

    private fun pressBack() {
        // Navigation settles on a coroutine, so the handler being pressed may not be
        // composed yet. Pressing early finishes the Activity and the test then fails with
        // no hierarchy to look at.
        rule.waitForIdle()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    /**
     * A folder's chip, matched inside the strip.
     *
     * Scoped rather than a bare `onNodeWithText`, because the same name appears again in
     * the move dialog's radio list and once more inside the name field -- three nodes for
     * one folder, all of them saying "Work".
     */
    private fun chip(name: String) = rule.onNode(
        hasText(name) and hasAnyAncestor(hasTestTag(DashboardTags.FOLDER_STRIP)),
        useUnmergedTree = true,
    )

    /**
     * Something inside whichever dialog is open, for the same reason.
     *
     * Unmerged because a `TextButton` merges its label into itself, and the merged tree
     * then loses the panel's `testTag` from the button's ancestry -- so a merged lookup
     * finds nothing at all.
     */
    private fun inDialog(text: String) = rule.onNode(
        hasText(text) and hasAnyAncestor(hasTestTag(OverlayTags.DIALOG)),
        useUnmergedTree = true,
    )

    /** The library loads on a background coroutine, which Compose does not idle on. */
    private fun awaitCards() = rule.waitUntil(TimeUnit.SECONDS.toMillis(5)) {
        rule.onAllNodesWithTag(DashboardTags.CARD_LIST).fetchSemanticsNodes().isNotEmpty()
    }

    /** Makes a folder through the strip, and returns once its chip is on screen. */
    private fun createFolder(name: String) {
        rule.onNodeWithTag(DashboardTags.NEW_FOLDER).performClick()
        rule.onNodeWithTag(DashboardTags.FOLDER_NAME_FIELD).performTextInput(name)
        inDialog(label(R.string.folder_create_confirm)).performClick()
        rule.waitUntil(TimeUnit.SECONDS.toMillis(5)) {
            rule.onAllNodes(
                hasText(name) and hasAnyAncestor(hasTestTag(DashboardTags.FOLDER_STRIP)),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun moveIntoFolder(entry: LibraryEntry, folderName: String?) {
        rule.onNodeWithTag(DashboardTags.card(entry.uri)).performTouchInput { longClick() }
        rule.onNodeWithText(label(R.string.folder_move)).performClick()
        inDialog(folderName ?: label(R.string.folder_move_none)).performClick()
        inDialog(label(R.string.folder_move_confirm)).performClick()
        rule.waitForIdle()
    }

    @Test
    fun creatingAFolderPutsAChipOnTheStrip() {
        awaitCards()

        createFolder("Work")

        rule.onNodeWithTag(DashboardTags.FOLDER_STRIP).assertIsDisplayed()
        chip("Work").assertIsDisplayed()
    }

    @Test
    fun aNewFolderIsSelectedSoTheNextImportLandsInIt() {
        awaitCards()

        createFolder("Work")

        // Making a folder is the first half of filling one, so the app moves into it --
        // which is also what makes the + button file into it.
        rule.onNodeWithText(label(R.string.no_folder_documents_title)).assertIsDisplayed()
    }

    @Test
    fun aBlankNameIsRefusedWithAReasonRatherThanSilently() {
        awaitCards()

        rule.onNodeWithTag(DashboardTags.NEW_FOLDER).performClick()
        inDialog(label(R.string.folder_create_confirm)).performClick()

        // The dialog is still open, saying why -- a rejected name that closed the dialog
        // and did nothing would look exactly like the folder failing to save.
        inDialog(label(R.string.folder_name_blank)).assertIsDisplayed()
        // Unmerged: the scrim's `clickable` makes it a merging root, so every plain node
        // inside the panel -- this Text among them -- is absorbed into it, tag and all.
        // A text field or a button survives because it is a merging root of its own.
        rule.onNodeWithTag(DashboardTags.FOLDER_NAME_ERROR, useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(DashboardTags.FOLDER_NAME_FIELD).assertIsDisplayed()
    }

    @Test
    fun theErrorIsHeldBackUntilTheFirstAttempt() {
        awaitCards()

        rule.onNodeWithTag(DashboardTags.NEW_FOLDER).performClick()

        // Greeting the user with "give the folder a name" before they have typed reads as
        // the dialog telling them off for opening it.
        rule.onNodeWithTag(DashboardTags.FOLDER_NAME_ERROR, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun aDuplicateNameIsRefused() {
        awaitCards()
        createFolder("Work")

        rule.onNodeWithTag(DashboardTags.NEW_FOLDER).performClick()
        rule.onNodeWithTag(DashboardTags.FOLDER_NAME_FIELD).performTextInput("work")
        inDialog(label(R.string.folder_create_confirm)).performClick()

        inDialog(label(R.string.folder_name_duplicate)).assertIsDisplayed()
    }

    @Test
    fun cancellingTheDialogMakesNoFolder() {
        awaitCards()

        rule.onNodeWithTag(DashboardTags.NEW_FOLDER).performClick()
        rule.onNodeWithTag(DashboardTags.FOLDER_NAME_FIELD).performTextInput("Work")
        inDialog(label(R.string.cancel)).performClick()

        chip("Work").assertDoesNotExist()
    }

    @Test
    fun movingADocumentFilesItUnderThatFolder() {
        awaitCards()
        createFolder("Work")
        // Back out to the whole list, where the card to move actually is.
        pressBack()

        moveIntoFolder(NOTES, "Work")
        chip("Work").performClick()

        rule.onNodeWithTag(DashboardTags.card(NOTES.uri)).assertIsDisplayed()
        rule.onNodeWithTag(DashboardTags.card(THOUGHTS.uri)).assertDoesNotExist()
    }

    @Test
    fun aFiledDocumentIsStillInRecent() {
        awaitCards()
        createFolder("Work")
        pressBack()

        moveIntoFolder(NOTES, "Work")

        // Recent is a log of what the user opened, not a bucket that filing empties.
        // Filing is a second way to find a document, the way starring already is.
        rule.onNodeWithTag(DashboardTags.card(NOTES.uri)).assertIsDisplayed()
        rule.onNodeWithTag(DashboardTags.card(THOUGHTS.uri)).assertIsDisplayed()
    }

    @Test
    fun tappingTheSelectedChipClearsTheFilter() {
        awaitCards()
        createFolder("Work")
        pressBack()
        moveIntoFolder(NOTES, "Work")

        chip("Work").performClick()
        chip("Work").performClick()

        rule.onNodeWithTag(DashboardTags.card(THOUGHTS.uri)).assertIsDisplayed()
    }

    @Test
    fun backLeavesTheFolderBeforeItLeavesTheApp() {
        awaitCards()
        createFolder("Work")
        pressBack()
        moveIntoFolder(NOTES, "Work")
        chip("Work").performClick()

        pressBack()

        // One step at a time: out of the folder first, and the Activity is still up.
        rule.onNodeWithTag(DashboardTags.card(THOUGHTS.uri)).assertIsDisplayed()
    }

    @Test
    fun unfilingADocumentTakesItBackOutOfTheFolder() {
        awaitCards()
        createFolder("Work")
        pressBack()
        moveIntoFolder(NOTES, "Work")

        moveIntoFolder(NOTES, null)
        chip("Work").performClick()

        rule.onNodeWithText(label(R.string.no_folder_documents_title)).assertIsDisplayed()
    }

    @Test
    fun renamingAFolderKeepsWhatIsFiledInIt() {
        awaitCards()
        createFolder("Work")
        pressBack()
        moveIntoFolder(NOTES, "Work")

        chip("Work").performTouchInput { longClick() }
        rule.onNodeWithText(label(R.string.folder_rename)).performClick()
        rule.onNodeWithTag(DashboardTags.FOLDER_NAME_FIELD).performTextClearance()
        rule.onNodeWithTag(DashboardTags.FOLDER_NAME_FIELD).performTextInput("Projects")
        inDialog(label(R.string.folder_rename_confirm)).performClick()

        chip("Projects").performClick()
        rule.onNodeWithTag(DashboardTags.card(NOTES.uri)).assertIsDisplayed()
    }

    @Test
    fun deletingAFolderLeavesItsDocumentsInRecent() {
        awaitCards()
        createFolder("Work")
        pressBack()
        moveIntoFolder(NOTES, "Work")

        chip("Work").performTouchInput { longClick() }
        rule.onNodeWithText(label(R.string.folder_delete)).performClick()
        inDialog(label(R.string.folder_delete_confirm)).performClick()

        // A folder is a label. Deleting one must not cost the user the documents wearing
        // it, and there was never a directory to delete either way.
        chip("Work").assertDoesNotExist()
        rule.onNodeWithTag(DashboardTags.card(NOTES.uri)).assertIsDisplayed()
        rule.onNodeWithTag(DashboardTags.card(THOUGHTS.uri)).assertIsDisplayed()
    }

    @Test
    fun deletingTheSelectedFolderGoesBackToEverything() {
        awaitCards()
        createFolder("Work")

        // Still inside the folder here: the filter would otherwise point at something
        // gone, showing an empty list with no chip selected to explain why.
        chip("Work").performTouchInput { longClick() }
        rule.onNodeWithText(label(R.string.folder_delete)).performClick()
        inDialog(label(R.string.folder_delete_confirm)).performClick()

        rule.onNodeWithTag(DashboardTags.card(NOTES.uri)).assertIsDisplayed()
    }

    @Test
    fun theFavouritesTabHasNoFolderStrip() {
        awaitCards()
        createFolder("Work")

        rule.onNodeWithTag(DashboardTags.tab(DashboardTab.Favorites.name)).performClick()

        rule.onNodeWithTag(DashboardTags.FOLDER_STRIP).assertDoesNotExist()
    }

    private companion object {
        val NOTES = LibraryEntry(
            uri = "content://seed/notes.md",
            displayName = "notes.md",
            title = "Meeting notes",
            excerpt = "What everyone agreed to do next.",
            lastOpened = System.currentTimeMillis(),
        )

        val THOUGHTS = LibraryEntry(
            uri = "content://seed/untitled-thoughts.md",
            displayName = "untitled-thoughts.md",
            excerpt = "No heading anywhere in this one.",
            lastOpened = System.currentTimeMillis() - 60_000,
        )

        val SEEDED = listOf(NOTES, THOUGHTS)
    }
}
