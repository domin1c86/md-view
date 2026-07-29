package com.mdview

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mdview.markdown.DocumentImages
import com.mdview.markdown.ImageModel
import com.mdview.markdown.LocalDocumentImages
import com.mdview.markdown.LocalRemoteImages
import com.mdview.markdown.MarkdownBlock
import com.mdview.markdown.MarkdownParser
import com.mdview.markdown.MarkdownTags
import com.mdview.markdown.RemoteImageAccess
import com.mdview.markdown.children
import com.mdview.ui.theme.BuiltInSkins
import com.mdview.ui.theme.MdViewTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How a figure behaves when its image cannot be resolved.
 *
 * Driven through the public [MarkdownBlock] rather than the private `MarkdownImage`, so
 * nothing has to be made visible for the sake of a test. No Activity and no storage wipe:
 * everything here is decided by the [DocumentImages] the local provides.
 */
@RunWith(AndroidJUnit4::class)
class MarkdownImageTest {

    @get:Rule
    val rule = createComposeRule()

    private class FakeImages(
        private val model: ImageModel,
        override val canRequestFolder: Boolean = true,
    ) : DocumentImages {
        var asked = 0
            private set

        var lastDestination: String? = null
            private set

        override fun modelFor(destination: String?): ImageModel {
            lastDestination = destination
            return model
        }

        override fun requestFolder() {
            asked++
        }
    }

    private fun show(
        source: String,
        images: FakeImages,
        remote: RemoteImageAccess = RemoteImageAccess.Allowed,
    ) {
        rule.setContent {
            MdViewTheme(skin = BuiltInSkins.Paper) {
                CompositionLocalProvider(
                    LocalDocumentImages provides images,
                    LocalRemoteImages provides remote,
                ) {
                    MarkdownParser.parse(source).children().forEach { MarkdownBlock(it) }
                }
            }
        }
    }

    private fun notice(id: Int, vararg args: Any): String =
        TestStorage.context.getString(id, *args)

    @Test
    fun anUnresolvedLocalImageOffersToAskForTheFolder() {
        val images = FakeImages(ImageModel.NeedsFolder)
        show("![a diagram](./images/diagram.png)", images)

        rule.onNodeWithTag(MarkdownTags.IMAGE_ACTION).assertIsDisplayed().performClick()

        assertEquals(1, images.asked)
        assertEquals("./images/diagram.png", images.lastDestination)
    }

    @Test
    fun theOfferIsWithheldWhereThereIsNoPickerToLaunch() {
        // The dashboard and @Preview both resolve through DocumentImages.None. Showing a
        // tappable invitation there would be an invitation to nothing.
        val images = FakeImages(ImageModel.NeedsFolder, canRequestFolder = false)
        show("![a diagram](./images/diagram.png)", images)

        rule.onAllNodesWithTag(MarkdownTags.IMAGE_ACTION).assertCountEquals(0)
        rule.onNodeWithText(notice(R.string.image_failed, "a diagram")).assertIsDisplayed()
    }

    @Test
    fun aGrantedButUnusableFolderSaysSoRatherThanAskingAgain() {
        val images = FakeImages(ImageModel.WrongFolder)
        show("![a diagram](./images/diagram.png)", images)

        rule.onNodeWithText(notice(R.string.image_wrong_folder)).assertIsDisplayed()
        rule.onNodeWithTag(MarkdownTags.IMAGE_ACTION).performClick()
        assertEquals(1, images.asked)
    }

    @Test
    fun anUnusableDestinationIsAnApologyRatherThanAnInvitation() {
        val images = FakeImages(ImageModel.Unusable)
        show("![a diagram](../../../etc/passwd)", images)

        rule.onAllNodesWithTag(MarkdownTags.IMAGE_ACTION).assertCountEquals(0)
        rule.onNodeWithText(notice(R.string.image_failed, "a diagram")).assertIsDisplayed()
    }

    @Test
    fun aResolvedImageShowsNoNoticeOfItsOwn() {
        val images = FakeImages(ImageModel.Fetch("content://provider/tree/x/document/y"))
        show("![a diagram](./images/diagram.png)", images)

        // Whatever Coil ends up doing with the model, it is not the folder invitation.
        rule.onAllNodesWithTag(MarkdownTags.IMAGE_ACTION).assertCountEquals(0)
    }

    @Test
    fun aBlockedRemoteImageNeverReachesTheFolderResolver() {
        // The privacy gate runs first and returns, so nothing below it can offer to widen
        // access on behalf of a document the user has already said no to.
        val images = FakeImages(ImageModel.NeedsFolder)
        show("![tracker](https://example.com/pixel.png)", images, RemoteImageAccess.Blocked)

        rule.onAllNodesWithTag(MarkdownTags.IMAGE_ACTION).assertCountEquals(0)
        assertEquals(null, images.lastDestination)
    }

    @Test
    fun anImageInsideASentenceStaysAPlaceholderAndAsksForNothing() {
        val images = FakeImages(ImageModel.NeedsFolder)
        show("See ![the chart](./chart.png) for the numbers.", images)

        rule.onAllNodesWithTag(MarkdownTags.IMAGE_ACTION).assertCountEquals(0)
        assertEquals(null, images.lastDestination)
    }
}
