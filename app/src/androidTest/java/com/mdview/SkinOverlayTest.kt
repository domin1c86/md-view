package com.mdview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mdview.ui.OverlayTags
import com.mdview.ui.SkinDialog
import com.mdview.ui.SkinDropdownMenu
import com.mdview.ui.theme.BuiltInSkins
import com.mdview.ui.theme.MdViewTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The two floating surfaces, driven directly.
 *
 * No Activity and no storage wipe: nothing here touches a store, and both composables are
 * public. The assertions that matter are the ones covering behaviour the platform used to
 * provide for free -- above all the scrim's dismiss, which replaced
 * `dismissOnClickOutside` when the dialog window went full-screen and left no "outside"
 * for it to fire on.
 */
@RunWith(AndroidJUnit4::class)
class SkinOverlayTest {

    @get:Rule
    val rule = createComposeRule()

    private var dismissed = 0
    private var confirmed = 0

    private fun showDialog(initiallyVisible: Boolean = true) {
        rule.setContent {
            MdViewTheme(skin = BuiltInSkins.Paper) {
                var visible by remember { mutableStateOf(initiallyVisible) }
                Box(Modifier.fillMaxSize()) {
                    SkinDialog(
                        visible = visible,
                        onDismissRequest = {
                            dismissed++
                            visible = false
                        },
                        title = TITLE,
                        body = BODY,
                        confirmLabel = CONFIRM,
                        onConfirm = {
                            confirmed++
                            visible = false
                        },
                        dismissLabel = CANCEL,
                    )
                }
            }
        }
    }

    @Test
    fun theDialogDrawsItsTitleBodyAndBothActions() {
        showDialog()

        rule.onNodeWithText(TITLE).assertIsDisplayed()
        rule.onNodeWithText(BODY).assertIsDisplayed()
        rule.onNodeWithText(CONFIRM).assertIsDisplayed()
        rule.onNodeWithText(CANCEL).assertIsDisplayed()
    }

    @Test
    fun tappingTheScrimDismissesIt() {
        showDialog()

        // Near the top edge rather than the centre: the panel is centred, and a click at
        // the scrim's own centre would land on the panel and be absorbed -- which is
        // exactly what the next test asserts.
        rule.onNodeWithTag(OverlayTags.DIALOG_SCRIM)
            .performTouchInput { click(Offset(width / 2f, 8f)) }
        rule.waitForIdle()

        assertEquals("the scrim did not dismiss the dialog", 1, dismissed)
        rule.onNodeWithText(TITLE).assertDoesNotExist()
    }

    @Test
    fun tappingThePanelDoesNotDismissIt() {
        showDialog()

        // Through the body text rather than the panel's tag: that is the tap a reader
        // actually makes, and the panel's own node is merged away by its children.
        rule.onNodeWithText(BODY).performClick()
        rule.waitForIdle()

        // Without the panel's own pointer handler the tap would fall through to the scrim
        // behind it, and reading the dialog would close it.
        assertEquals("a tap on the panel reached the scrim", 0, dismissed)
        rule.onNodeWithText(TITLE).assertIsDisplayed()
    }

    @Test
    fun confirmingRunsTheActionAndClosesTheDialog() {
        showDialog()

        rule.onNodeWithText(CONFIRM).performClick()
        rule.waitForIdle()

        assertEquals(1, confirmed)
        assertEquals("confirming should not also count as a dismissal", 0, dismissed)
        rule.onNodeWithText(TITLE).assertDoesNotExist()
    }

    @Test
    fun cancellingDismissesWithoutConfirming() {
        showDialog()

        rule.onNodeWithText(CANCEL).performClick()
        rule.waitForIdle()

        assertEquals(1, dismissed)
        assertEquals(0, confirmed)
        rule.onNodeWithText(TITLE).assertDoesNotExist()
    }

    @Test
    fun aDialogThatWasNeverVisibleComposesNothingAtAll() {
        // The early return is what keeps a dialog off the screen; if it ever stopped
        // working the window would appear empty rather than not at all.
        showDialog(initiallyVisible = false)

        rule.onNodeWithText(TITLE).assertDoesNotExist()
        rule.onNodeWithTag(OverlayTags.DIALOG_SCRIM).assertDoesNotExist()
        rule.onNodeWithTag(OverlayTags.DIALOG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theMenuShowsItsItemsAndTheyClick() {
        var picked = 0
        rule.setContent {
            MdViewTheme(skin = BuiltInSkins.Ink) {
                var open by remember { mutableStateOf(true) }
                Box(Modifier.fillMaxSize()) {
                    SkinDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        DropdownMenuItem(
                            text = { Text(ITEM) },
                            onClick = {
                                picked++
                                open = false
                            },
                        )
                    }
                }
            }
        }

        rule.onNodeWithText(ITEM).assertIsDisplayed()
        rule.onNodeWithText(ITEM).performClick()
        rule.waitForIdle()

        assertEquals(1, picked)
        rule.onNodeWithText(ITEM).assertDoesNotExist()
    }

    private companion object {
        const val TITLE = "Unsaved changes"
        const val BODY = "Your edits have not been saved."
        const val CONFIRM = "Discard"
        const val CANCEL = "Keep editing"
        const val ITEM = "Open a file"
    }
}
