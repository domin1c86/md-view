package com.mdview

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mdview.ui.dashboard.DashboardTags
import com.mdview.ui.dashboard.SettingsTags
import com.mdview.ui.theme.BuiltInSkins
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

/** The skin picker: choosing, persisting, importing and removing. */
@RunWith(AndroidJUnit4::class)
class SkinSettingsTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(object : ExternalResource() {
            override fun before() = TestStorage.wipe()
        })
        .around(rule)

    private val settings get() = MdViewApplication.from(TestStorage.context).settings

    private val skins get() = MdViewApplication.from(TestStorage.context).skins

    private fun openSettings() {
        rule.onNodeWithTag(DashboardTags.tab(DashboardTab.Mine.name)).performClick()
    }

    private fun chooseSkin(dark: Boolean, id: String) {
        rule.onNodeWithTag(SettingsTags.skin(dark, id)).performScrollTo().performClick()
    }

    @Test
    fun theDefaultSkinsAreSelectedOnAFreshInstall() {
        openSettings()

        rule.onNodeWithTag(SettingsTags.skin(false, BuiltInSkins.Paper.id))
            .performScrollTo()
            .assertIsSelected()
        rule.onNodeWithTag(SettingsTags.skin(true, BuiltInSkins.Ink.id))
            .performScrollTo()
            .assertIsSelected()
    }

    @Test
    fun everyBuiltInSkinIsReachableByScrolling() {
        // The galleries are a FlowRow inside the panel's one vertical scroller. A nested
        // horizontal scroller here would break performScrollTo, which is what this guards.
        openSettings()

        BuiltInSkins.all.forEach { skin ->
            rule.onNodeWithTag(SettingsTags.skin(skin.dark, skin.id))
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    @Test
    fun choosingALightSkinMarksItSelectedAndDeselectsTheOldOne() {
        openSettings()
        chooseSkin(dark = false, id = BuiltInSkins.Cobalt.id)

        rule.onNodeWithTag(SettingsTags.skin(false, BuiltInSkins.Cobalt.id)).assertIsSelected()
        rule.onNodeWithTag(SettingsTags.skin(false, BuiltInSkins.Paper.id)).assertIsNotSelected()
    }

    @Test
    fun choosingASkinReachesTheStore() {
        openSettings()
        chooseSkin(dark = false, id = BuiltInSkins.Sepia.id)
        rule.waitUntil { settings.current.lightSkinId == BuiltInSkins.Sepia.id }

        chooseSkin(dark = true, id = BuiltInSkins.Midnight.id)
        rule.waitUntil { settings.current.darkSkinId == BuiltInSkins.Midnight.id }
    }

    @Test
    fun theLightAndDarkPickersAreIndependent() {
        openSettings()
        chooseSkin(dark = false, id = BuiltInSkins.Cobalt.id)
        rule.waitUntil { settings.current.lightSkinId == BuiltInSkins.Cobalt.id }

        // Same skin ids appear in both galleries; the tags must not collide.
        rule.onNodeWithTag(SettingsTags.skin(true, BuiltInSkins.Ink.id)).assertIsSelected()
    }

    @Test
    fun aChosenSkinSurvivesTheActivityBeingRecreated() {
        openSettings()
        chooseSkin(dark = true, id = BuiltInSkins.Nord.id)
        rule.waitUntil { settings.current.darkSkinId == BuiltInSkins.Nord.id }

        rule.activityRule.scenario.recreate()

        rule.onNodeWithTag(SettingsTags.skin(true, BuiltInSkins.Nord.id))
            .performScrollTo()
            .assertIsSelected()
    }

    @Test
    fun anImportedSkinAppearsInTheGalleryAndCanBeChosen() {
        // Driven through the store rather than the picker: instrumentation cannot operate
        // the system file dialog, and the picker's only job is to hand over these bytes.
        runBlocking {
            skins.import(
                """{"id":"tester","name":"Tester","base":"paper","colors":{"accent":"#FF00FF"}}"""
            )
        }

        openSettings()
        chooseSkin(dark = false, id = "tester")

        rule.waitUntil { settings.current.lightSkinId == "tester" }
        rule.onNodeWithTag(SettingsTags.skin(false, "tester")).assertIsSelected()
    }

    @Test
    fun onlyAnImportedSkinOffersToBeRemoved() {
        runBlocking { skins.import("""{"id":"tester","name":"Tester","base":"paper"}""") }
        openSettings()

        rule.onNodeWithTag(SettingsTags.skin(false, "tester")).performScrollTo()
        rule.onNodeWithTag(SettingsTags.deleteSkin("tester")).assertIsDisplayed()
        rule.onAllNodesWithTag(SettingsTags.deleteSkin(BuiltInSkins.Paper.id))
            .assertCountEquals(0)
    }

    @Test
    fun removingTheActiveSkinFallsBackToTheDefault() {
        runBlocking { skins.import("""{"id":"tester","name":"Tester","base":"paper"}""") }
        openSettings()
        chooseSkin(dark = false, id = "tester")
        rule.waitUntil { settings.current.lightSkinId == "tester" }

        rule.onNodeWithTag(SettingsTags.deleteSkin("tester")).performScrollTo().performClick()

        rule.waitUntil { settings.current.lightSkinId == BuiltInSkins.Paper.id }
        rule.onNodeWithTag(SettingsTags.skin(false, BuiltInSkins.Paper.id)).assertIsSelected()
        assertFalse(File(TestStorage.context.filesDir, "skins/tester.json").exists())
    }

    @Test
    fun theImportRowIsOfferedUnderTheGalleries() {
        openSettings()

        rule.onNodeWithTag(SettingsTags.IMPORT_SKIN).performScrollTo().assertIsDisplayed()
        // Nothing has gone wrong yet, so no error notice should be on screen.
        rule.onAllNodesWithTag(SettingsTags.IMPORT_ERROR).assertCountEquals(0)
    }
}
