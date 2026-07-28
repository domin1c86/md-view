package com.mdview

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mdview.data.ReadingSize
import com.mdview.data.RemoteImagePolicy
import com.mdview.data.Settings
import com.mdview.data.ThemeChoice
import com.mdview.ui.dashboard.DashboardTags
import com.mdview.ui.dashboard.SettingsTags
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

/** The Mine tab: that a preference sticks, and that it reaches the store. */
@RunWith(AndroidJUnit4::class)
class SettingsTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(object : ExternalResource() {
            override fun before() {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                File(context.filesDir, "drafts").deleteRecursively()
                File(context.filesDir, "library").deleteRecursively()
                File(context.filesDir, "settings.txt").delete()
                MdViewApplication.from(context).resetForTests()
            }
        })
        .around(rule)

    private val settings
        get() = MdViewApplication
            .from(InstrumentationRegistry.getInstrumentation().targetContext)
            .settings

    private fun openSettings() {
        rule.onNodeWithTag(DashboardTags.tab(DashboardTab.Mine.name)).performClick()
    }

    private fun choose(option: Enum<*>) {
        rule.onNodeWithTag(SettingsTags.option(option)).performScrollTo().performClick()
    }

    @Test
    fun theSettingsPanelIsBehindTheMineTab() {
        openSettings()

        rule.onNodeWithTag(SettingsTags.PANEL).assertIsDisplayed()
    }

    @Test
    fun theDefaultsAreSelectedOnAFreshInstall() {
        openSettings()

        rule.onNodeWithTag(SettingsTags.option(ThemeChoice.System)).assertIsSelected()
        rule.onNodeWithTag(SettingsTags.option(ReadingSize.Medium)).performScrollTo().assertIsSelected()
    }

    @Test
    fun choosingADarkThemeMarksItSelected() {
        openSettings()

        choose(ThemeChoice.Dark)

        rule.onNodeWithTag(SettingsTags.option(ThemeChoice.Dark)).assertIsSelected()
        rule.onNodeWithTag(SettingsTags.option(ThemeChoice.System)).assertIsNotSelected()
    }

    @Test
    fun choosingAThemeReachesTheStore() {
        openSettings()

        choose(ThemeChoice.Light)

        rule.waitUntil { settings.current.theme == ThemeChoice.Light }
    }

    @Test
    fun theReadingSizeReachesTheStore() {
        openSettings()

        choose(ReadingSize.Large)

        rule.waitUntil { settings.current.readingSize == ReadingSize.Large }
    }

    @Test
    fun blockingRemoteImagesReachesTheStore() {
        openSettings()

        choose(RemoteImagePolicy.Never)

        rule.waitUntil { settings.current.remoteImages == RemoteImagePolicy.Never }
    }

    @Test
    fun aChosenSettingSurvivesTheActivityBeingRecreated() {
        openSettings()
        choose(ThemeChoice.Dark)
        rule.waitUntil { settings.current.theme == ThemeChoice.Dark }

        rule.activityRule.scenario.recreate()

        // The tab is held in the ViewModel rather than a remember, so a recreate --
        // which is also what a language change does -- leaves the user where they were.
        rule.onNodeWithTag(SettingsTags.option(ThemeChoice.Dark)).assertIsSelected()
    }

    @Test
    fun settingsAreIndependentOfEachOther() {
        openSettings()

        choose(ThemeChoice.Dark)
        choose(ReadingSize.Small)
        choose(RemoteImagePolicy.Never)

        rule.waitUntil {
            settings.current == Settings(
                theme = ThemeChoice.Dark,
                readingSize = ReadingSize.Small,
                remoteImages = RemoteImagePolicy.Never,
            )
        }
    }
}
