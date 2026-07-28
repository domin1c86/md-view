package com.mdview

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mdview.ui.dashboard.DashboardTags
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Drives the real Activity: reach the editor from the dashboard, type Markdown, flip to
 * preview and check the rendered result. Covers everything except the file picker, which
 * needs a human.
 */
@RunWith(AndroidJUnit4::class)
class MdViewAppTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(object : ExternalResource() {
            override fun before() = TestStorage.wipe()
        })
        .around(rule)

    private fun label(resId: Int): String = rule.activity.getString(resId)

    private fun tab(tab: DashboardTab) =
        rule.onNodeWithTag(DashboardTags.tab(tab.name)).performClick()

    private fun pressBack() {
        // Navigation is a coroutine, so the destination may still be settling. Pressing
        // back before the target screen has composed its handler finishes the Activity
        // instead, and the test then fails with no hierarchy to look at.
        rule.waitForIdle()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    /** The dashboard is the launch screen, so every editor test starts by leaving it. */
    private fun openEditor() {
        rule.onNodeWithContentDescription(label(R.string.add_document)).performClick()
        rule.onNodeWithText(label(R.string.new_document)).performClick()
    }

    private fun typeSource(markdown: String) {
        openEditor()
        rule.onNode(hasSetTextAction()).performTextInput(markdown)
        rule.onNodeWithContentDescription(label(R.string.show_preview)).performClick()
    }

    // --- Dashboard ---------------------------------------------------------------

    @Test
    fun theDashboardIsTheLaunchScreen() {
        rule.onNodeWithText(label(R.string.no_recent_title)).assertIsDisplayed()
        rule.onNodeWithText(label(R.string.no_recent_body)).assertIsDisplayed()
    }

    @Test
    fun allThreeTabsAreOffered() {
        DashboardTab.entries.forEach {
            rule.onNodeWithTag(DashboardTags.tab(it.name)).assertIsDisplayed()
        }
    }

    @Test
    fun favouritesStartsEmptyAndSaysSo() {
        tab(DashboardTab.Favorites)

        rule.onNodeWithText(label(R.string.no_favorites_title)).assertIsDisplayed()
    }

    @Test
    fun theSelectedTabIsMarkedAsSuch() {
        tab(DashboardTab.Mine)

        rule.onNodeWithTag(DashboardTags.tab(DashboardTab.Mine.name)).assertIsSelected()
        rule.onNodeWithTag(DashboardTags.tab(DashboardTab.Recent.name)).assertIsNotSelected()
    }

    @Test
    fun backFromASecondaryTabReturnsToRecent() {
        tab(DashboardTab.Favorites)

        pressBack()

        rule.onNodeWithText(label(R.string.no_recent_title)).assertIsDisplayed()
    }

    @Test
    fun aNewDocumentReachesTheEditor() {
        openEditor()

        rule.onNode(hasSetTextAction()).assertIsDisplayed()
    }

    @Test
    fun leavingTheEditorReturnsToTheDashboard() {
        openEditor()

        rule.onNodeWithContentDescription(label(R.string.back_to_list)).performClick()

        rule.onNodeWithTag(DashboardTags.tab(DashboardTab.Recent.name)).assertIsDisplayed()
    }

    @Test
    fun backLeavesTheEditorToo() {
        openEditor()

        pressBack()

        rule.onNodeWithTag(DashboardTags.tab(DashboardTab.Recent.name)).assertIsDisplayed()
    }

    @Test
    fun unsavedScratchWorkIsOfferedBackOnTheDashboard() {
        openEditor()
        rule.onNode(hasSetTextAction()).performTextInput("something worth keeping")

        rule.onNodeWithContentDescription(label(R.string.back_to_list)).performClick()

        // Leaving is not destructive -- the draft is written on the way out and the
        // dashboard offers it back rather than silently dropping it.
        rule.onNodeWithTag(DashboardTags.DRAFT_CARD).assertIsDisplayed()
    }

    @Test
    fun theOfferedDraftOpensWithItsTextIntact() {
        openEditor()
        rule.onNode(hasSetTextAction()).performTextInput("something worth keeping")
        rule.onNodeWithContentDescription(label(R.string.back_to_list)).performClick()

        rule.onNodeWithTag(DashboardTags.DRAFT_CARD).performClick()

        rule.onNodeWithText("something worth keeping").assertIsDisplayed()
    }

    // --- Rendering ---------------------------------------------------------------

    @Test
    fun headingsAndListsRenderInPreview() {
        typeSource("# Release notes\n\n- first item\n- second item\n")

        rule.onNodeWithText("Release notes").assertIsDisplayed()
        rule.onNodeWithText("first item").assertIsDisplayed()
        rule.onNodeWithText("second item").assertIsDisplayed()
    }

    @Test
    fun inlineFormattingLosesItsMarkersInPreview() {
        typeSource("Some **bold** and `code` text.")

        // The markers are gone; only the styled words remain.
        rule.onNodeWithText("Some bold and code text.").assertIsDisplayed()
    }

    @Test
    fun codeBlocksAndQuotesRenderInPreview() {
        typeSource("> quoted line\n\n```kotlin\nfun main() = Unit\n```\n")

        rule.onNodeWithText("quoted line").assertIsDisplayed()
        rule.onNodeWithText("kotlin").assertIsDisplayed()
        rule.onNodeWithText("fun main() = Unit").assertIsDisplayed()
    }

    @Test
    fun frontMatterDoesNotBecomeAHeading() {
        typeSource("---\ntitle: Metadata\n---\n\n# Actual heading\n")

        rule.onNodeWithText("Actual heading").assertIsDisplayed()
        rule.onNodeWithText("title: Metadata").assertDoesNotExist()
    }

    // --- Editing -----------------------------------------------------------------

    @Test
    fun editingMarksTheDocumentDirty() {
        openEditor()
        rule.onNode(hasSetTextAction()).performTextInput("draft")

        rule.onNodeWithText("${label(R.string.untitled)} •").assertIsDisplayed()
    }

    @Test
    fun switchingModesKeepsTheSourceText() {
        typeSource("keep me")

        rule.onNodeWithContentDescription(label(R.string.show_source)).performClick()
        rule.onNodeWithText("keep me").assertIsDisplayed()
    }

    @Test
    fun savingIsDisabledUntilThereIsSomethingToSave() {
        openEditor()

        rule.onNodeWithContentDescription(label(R.string.save)).assertIsNotEnabled()
    }

    @Test
    fun undoIsOfferedOnlyOnceThereIsSomethingToUndo() {
        openEditor()
        rule.onNodeWithContentDescription(label(R.string.undo)).assertIsNotEnabled()

        rule.onNode(hasSetTextAction()).performTextInput("a mistake")

        rule.onNodeWithContentDescription(label(R.string.undo)).assertIsEnabled()
    }

    @Test
    fun undoTakesTheTypedTextBackOut() {
        openEditor()
        rule.onNode(hasSetTextAction()).performTextInput("a mistake")

        rule.onNodeWithContentDescription(label(R.string.undo)).performClick()

        rule.onNodeWithText("a mistake").assertDoesNotExist()
        rule.onNodeWithContentDescription(label(R.string.redo)).assertIsEnabled()
    }

    @Test
    fun redoPutsItBack() {
        openEditor()
        rule.onNode(hasSetTextAction()).performTextInput("second thoughts")
        rule.onNodeWithContentDescription(label(R.string.undo)).performClick()

        rule.onNodeWithContentDescription(label(R.string.redo)).performClick()

        rule.onNodeWithText("second thoughts").assertIsDisplayed()
    }

    @Test
    fun undoAndRedoAreHiddenInPreview() {
        typeSource("anything at all")

        rule.onNodeWithContentDescription(label(R.string.undo)).assertDoesNotExist()
        rule.onNodeWithContentDescription(label(R.string.redo)).assertDoesNotExist()
    }

    @Test
    fun startingANewDocumentEmptiesTheEditor() {
        openEditor()
        rule.onNode(hasSetTextAction()).performTextInput("throwaway")

        // Unsaved text, so the discard dialog stands between here and the empty buffer.
        rule.onNodeWithContentDescription(label(R.string.more_actions)).performClick()
        rule.onNodeWithText(label(R.string.new_document)).performClick()
        rule.onNodeWithText(label(R.string.discard)).performClick()

        rule.onNodeWithText("throwaway").assertDoesNotExist()
    }
}
