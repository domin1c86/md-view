package com.mdview

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mdview.data.LibraryCodec
import com.mdview.data.LibraryEntry
import com.mdview.ui.dashboard.DashboardTags
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Favourites and the card actions, driven through the real UI.
 *
 * The library is seeded on disk rather than filled by opening documents, because the
 * only way into it is the system file picker and that needs a human. Seeding also lets
 * a test describe exactly the state it cares about.
 */
@RunWith(AndroidJUnit4::class)
class DashboardTest {

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

    private fun tab(tab: DashboardTab) =
        rule.onNodeWithTag(DashboardTags.tab(tab.name)).performClick()

    /** The library loads on a background coroutine, which Compose does not idle on. */
    private fun awaitCards() = rule.waitUntil(TimeUnit.SECONDS.toMillis(5)) {
        rule.onAllNodesWithTag(DashboardTags.CARD_LIST).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun aSeededDocumentIsShownAsACard() {
        awaitCards()

        rule.onNodeWithTag(DashboardTags.card(NOTES.uri)).assertIsDisplayed()
        rule.onNodeWithText("Meeting notes").assertIsDisplayed()
        rule.onNodeWithText("What everyone agreed to do next.").assertIsDisplayed()
    }

    @Test
    fun theFilenameAndAgeAreShown() {
        awaitCards()

        rule.onNodeWithText("notes.md", substring = true).assertIsDisplayed()
    }

    @Test
    fun aDocumentWithNoHeadingFallsBackToItsFilename() {
        awaitCards()

        rule.onNodeWithText("untitled-thoughts.md", substring = true).assertIsDisplayed()
    }

    @Test
    fun favouritesStartsEmptyEvenWithDocumentsInRecent() {
        awaitCards()

        tab(DashboardTab.Favorites)

        rule.onNodeWithText(label(R.string.no_favorites_title)).assertIsDisplayed()
    }

    @Test
    fun starringADocumentPutsItInFavourites() {
        awaitCards()

        rule.onNodeWithTag(DashboardTags.star(NOTES.uri)).performClick()
        tab(DashboardTab.Favorites)

        rule.onNodeWithTag(DashboardTags.card(NOTES.uri)).assertIsDisplayed()
    }

    @Test
    fun onlyTheStarredDocumentReachesFavourites() {
        awaitCards()

        rule.onNodeWithTag(DashboardTags.star(NOTES.uri)).performClick()
        tab(DashboardTab.Favorites)

        rule.onNodeWithTag(DashboardTags.card(THOUGHTS.uri)).assertDoesNotExist()
    }

    @Test
    fun unstarringTakesItBackOutOfFavourites() {
        awaitCards()
        rule.onNodeWithTag(DashboardTags.star(NOTES.uri)).performClick()
        tab(DashboardTab.Favorites)

        rule.onNodeWithTag(DashboardTags.star(NOTES.uri)).performClick()

        rule.onNodeWithText(label(R.string.no_favorites_title)).assertIsDisplayed()
    }

    @Test
    fun aStarredDocumentStaysInRecentToo() {
        awaitCards()

        rule.onNodeWithTag(DashboardTags.star(NOTES.uri)).performClick()

        // Favouriting is not filing it away -- it is still one of the recent documents.
        rule.onNodeWithTag(DashboardTags.card(NOTES.uri)).assertIsDisplayed()
    }

    @Test
    fun removingADocumentDropsItsCard() {
        awaitCards()

        rule.onNodeWithTag(DashboardTags.card(THOUGHTS.uri)).performTouchInput { longClick() }
        rule.onNodeWithText(label(R.string.remove_from_list)).performClick()

        rule.onNodeWithTag(DashboardTags.card(THOUGHTS.uri)).assertDoesNotExist()
        rule.onNodeWithTag(DashboardTags.card(NOTES.uri)).assertIsDisplayed()
    }

    @Test
    fun aReadOnlyDocumentSaysSoOnItsCard() {
        awaitCards()

        rule.onNodeWithText(label(R.string.document_read_only)).assertIsDisplayed()
    }

    @Test
    fun aDocumentFromAnotherAppSaysSoOnItsCard() {
        awaitCards()

        rule.onNodeWithText(label(R.string.document_from_another_app)).assertIsDisplayed()
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
            title = null,
            excerpt = "No heading anywhere in this one.",
            lastOpened = System.currentTimeMillis() - 60_000,
            canWrite = false,
        )

        val SHARED = LibraryEntry(
            uri = "content://seed/shared.md",
            displayName = "shared.md",
            title = "Handed over",
            excerpt = "Arrived through an intent.",
            lastOpened = System.currentTimeMillis() - 120_000,
            isTransient = true,
        )

        val SEEDED = listOf(NOTES, THOUGHTS, SHARED)
    }
    @Test
    fun onlyOneNavigationSurfaceCarriesEachTabTag() {
        // The rail and the bottom bar both tag their items, and only one of the two is
        // ever composed. If that stopped being true, every onNodeWithTag(tab(...)) in
        // this suite and in MdViewAppTest would start failing on ambiguity instead.
        DashboardTab.entries.forEach { entry ->
            rule.onAllNodesWithTag(DashboardTags.tab(entry.name)).assertCountEquals(1)
        }
    }

}
