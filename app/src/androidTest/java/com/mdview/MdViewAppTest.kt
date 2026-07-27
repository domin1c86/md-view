package com.mdview

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real Activity: type Markdown in the source editor, flip to preview and
 * check the rendered result. Covers both MVP features without touching the file
 * picker, which needs a human.
 */
@RunWith(AndroidJUnit4::class)
class MdViewAppTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

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
}
