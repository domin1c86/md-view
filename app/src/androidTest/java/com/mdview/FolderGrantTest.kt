package com.mdview

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mdview.data.FolderGrant
import com.mdview.ui.dashboard.DashboardTags
import com.mdview.ui.dashboard.SettingsTags
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Mine → Image folders: that a grant is listed, and that revoking it sticks.
 *
 * Grants are put in through the store rather than through `OpenDocumentTree`, which
 * instrumentation cannot operate — the same approach `SkinSettingsTest` takes to the skin
 * import picker. What the dialog contributes, a real tree URI and a real permission, is on
 * the manual checklist in `HANDOFF.md` instead.
 */
@RunWith(AndroidJUnit4::class)
class FolderGrantTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(object : ExternalResource() {
            override fun before() = TestStorage.wipe()
        })
        .around(rule)

    private val store get() = MdViewApplication.from(TestStorage.context).folders

    private fun openSettings() {
        rule.onNodeWithTag(DashboardTags.tab(DashboardTab.Mine.name)).performClick()
    }

    private fun grant(id: Int, name: String = "Folder $id") = FolderGrant(
        treeUri = "content://com.android.externalstorage.documents/tree/primary%3AFolder$id",
        displayName = name,
        grantedAt = id.toLong(),
    )

    private fun give(vararg grants: FolderGrant) {
        runBlocking { grants.forEach { store.add(it) } }
        rule.waitUntil { store.grants.value.size == grants.size }
    }

    @Test
    fun aFreshInstallHasNoFoldersAndSaysSo() {
        openSettings()

        rule.onNodeWithTag(SettingsTags.FOLDERS).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(SettingsTags.FOLDERS_EMPTY).assertIsDisplayed()
    }

    @Test
    fun aGrantedFolderIsListedByName() {
        give(grant(1, "Notes"))
        openSettings()

        rule.onNodeWithTag(SettingsTags.folder(grant(1).treeUri)).performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithTag(SettingsTags.FOLDERS_EMPTY).assertCountEquals(0)
    }

    @Test
    fun everyGrantedFolderIsReachableWithinTheOneScroller() {
        // The Mine tab has to stay a single vertical scroll container. A nested list here
        // would break performScrollTo for this suite and the two settings suites with it.
        val many = (1..5).map { grant(it) }
        give(*many.toTypedArray())
        openSettings()

        many.forEach { folder ->
            rule.onNodeWithTag(SettingsTags.folder(folder.treeUri)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun revokingAFolderRemovesItFromTheListAndTheStore() {
        give(grant(1), grant(2))
        openSettings()

        rule.onNodeWithTag(SettingsTags.forgetFolder(grant(1).treeUri))
            .performScrollTo()
            .performClick()

        rule.waitUntil { store.grants.value.none { it.treeUri == grant(1).treeUri } }
        rule.onAllNodesWithTag(SettingsTags.folder(grant(1).treeUri)).assertCountEquals(0)
        rule.onNodeWithTag(SettingsTags.folder(grant(2).treeUri)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun revokingTheLastFolderBringsBackTheEmptyNotice() {
        give(grant(1))
        openSettings()

        rule.onNodeWithTag(SettingsTags.forgetFolder(grant(1).treeUri))
            .performScrollTo()
            .performClick()

        rule.waitUntil { store.grants.value.isEmpty() }
        rule.onNodeWithTag(SettingsTags.FOLDERS_EMPTY).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aGrantSurvivesTheActivityBeingRecreated() {
        give(grant(1, "Notes"))
        openSettings()

        rule.activityRule.scenario.recreate()

        rule.onNodeWithTag(SettingsTags.folder(grant(1).treeUri)).performScrollTo().assertIsDisplayed()
    }
}
