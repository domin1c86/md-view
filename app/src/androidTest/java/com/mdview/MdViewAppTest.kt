package com.mdview

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

/**
 * Drives the real Activity: type Markdown in the source editor, flip to preview and
 * check the rendered result. Covers both MVP features without touching the file
 * picker, which needs a human.
 */
@RunWith(AndroidJUnit4::class)
class MdViewAppTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Autosaved drafts outlive the process by design, which also means they outlive a
     * test. Wiping them has to happen before the Activity starts -- by the time an
     * `@Before` method runs, the ViewModel has already restored one -- so the cleanup
     * is chained outside the Compose rule rather than written as a setup method.
     */
    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(object : ExternalResource() {
            override fun before() {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                File(context.filesDir, "drafts").deleteRecursively()
            }
        })
        .around(rule)

    private fun label(resId: Int): String = rule.activity.getString(resId)

    private fun typeSource(markdown: String) {
        rule.onNodeWithContentDescription(label(R.string.show_source)).performClick()
        rule.onNode(hasSetTextAction()).performTextInput(markdown)
        rule.onNodeWithContentDescription(label(R.string.show_preview)).performClick()
    }

    @Test
    fun emptyStateIsShownBeforeAnyDocumentIsOpen() {
        rule.onNodeWithText(label(R.string.empty_title)).assertIsDisplayed()
        rule.onNodeWithText(label(R.string.open_a_markdown_file)).assertIsDisplayed()
    }

    @Test
    fun savingIsDisabledUntilThereIsSomethingToSave() {
        rule.onNodeWithContentDescription(label(R.string.save)).assertIsNotEnabled()
    }

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
    fun editingMarksTheDocumentDirty() {
        rule.onNodeWithContentDescription(label(R.string.show_source)).performClick()
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
    fun frontMatterDoesNotBecomeAHeading() {
        typeSource("---\ntitle: Metadata\n---\n\n# Actual heading\n")

        rule.onNodeWithText("Actual heading").assertIsDisplayed()
        rule.onNodeWithText("title: Metadata").assertDoesNotExist()
    }

    @Test
    fun undoIsOfferedOnlyOnceThereIsSomethingToUndo() {
        rule.onNodeWithContentDescription(label(R.string.show_source)).performClick()
        rule.onNodeWithContentDescription(label(R.string.undo)).assertIsNotEnabled()

        rule.onNode(hasSetTextAction()).performTextInput("a mistake")

        rule.onNodeWithContentDescription(label(R.string.undo)).assertIsEnabled()
    }

    @Test
    fun undoTakesTheTypedTextBackOut() {
        rule.onNodeWithContentDescription(label(R.string.show_source)).performClick()
        rule.onNode(hasSetTextAction()).performTextInput("a mistake")

        rule.onNodeWithContentDescription(label(R.string.undo)).performClick()

        rule.onNodeWithText("a mistake").assertDoesNotExist()
        rule.onNodeWithContentDescription(label(R.string.redo)).assertIsEnabled()
    }

    @Test
    fun redoPutsItBack() {
        rule.onNodeWithContentDescription(label(R.string.show_source)).performClick()
        rule.onNode(hasSetTextAction()).performTextInput("second thoughts")
        rule.onNodeWithContentDescription(label(R.string.undo)).performClick()

        rule.onNodeWithContentDescription(label(R.string.redo)).performClick()

        rule.onNodeWithText("second thoughts").assertIsDisplayed()
    }

    @Test
    fun undoAndRedoAreHiddenInPreview() {
        rule.onNodeWithContentDescription(label(R.string.undo)).assertDoesNotExist()
        rule.onNodeWithContentDescription(label(R.string.redo)).assertDoesNotExist()
    }

    @Test
    fun startingANewDocumentEmptiesTheEditor() {
        rule.onNodeWithContentDescription(label(R.string.show_source)).performClick()
        rule.onNode(hasSetTextAction()).performTextInput("throwaway")

        // Unsaved text, so the discard dialog stands between here and the empty buffer.
        rule.onNodeWithContentDescription(label(R.string.more_actions)).performClick()
        rule.onNodeWithText(label(R.string.new_document)).performClick()
        rule.onNodeWithText(label(R.string.discard)).performClick()

        rule.onNodeWithText("throwaway").assertDoesNotExist()
    }

    @Test
    fun theEmptyStateOffersANewDocument() {
        rule.onNodeWithText(label(R.string.start_a_new_document)).performClick()

        rule.onNode(hasSetTextAction()).assertIsDisplayed()
    }
}
