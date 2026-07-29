package com.mdview

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * That the shipped translations actually resolve.
 *
 * Done by resolving resources against a configured context rather than by switching the
 * app's language and restarting it: the restart is the platform's to perform on API 33+,
 * and a test that waits on it is a test that flakes.
 */
@RunWith(AndroidJUnit4::class)
class LocalizationTest {

    private val base: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun localised(tag: String): Context {
        val config = Configuration(base.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag(tag)))
        }
        return base.createConfigurationContext(config)
    }

    private val english get() = localised("en")
    private val chinese get() = localised("zh-Hans")

    @Test
    fun theChineseStringsAreActuallyDifferent() {
        // A missing values-zh silently falls back to English, which would make every
        // other assertion here pass for the wrong reason.
        assertNotEquals(
            english.getString(R.string.tab_recent),
            chinese.getString(R.string.tab_recent),
        )
    }

    @Test
    fun everyTabIsTranslated() {
        assertEquals("最近", chinese.getString(R.string.tab_recent))
        assertEquals("收藏", chinese.getString(R.string.tab_favorites))
        assertEquals("我的", chinese.getString(R.string.tab_mine))
    }

    @Test
    fun theSettingsPanelIsTranslated() {
        listOf(
            R.string.setting_theme,
            R.string.setting_language,
            R.string.setting_reading_size,
            R.string.setting_remote_images,
            R.string.settings_appearance,
            R.string.settings_privacy,
            R.string.settings_folders,
            R.string.setting_folders_body,
            R.string.folders_empty,
            R.string.folder_forget,
            R.string.image_needs_folder,
            R.string.image_wrong_folder,
        ).forEach { id ->
            assertNotEquals(
                "untranslated: ${base.resources.getResourceEntryName(id)}",
                english.getString(id),
                chinese.getString(id),
            )
        }
    }

    @Test
    fun errorMessagesAreTranslated() {
        listOf(
            R.string.error_open_missing,
            R.string.error_open_too_large,
            R.string.error_open_not_text,
            R.string.error_save_denied,
        ).forEach { id ->
            assertNotEquals(english.getString(id), chinese.getString(id))
        }
    }

    @Test
    fun relativeTimePluralsResolveInBothLanguages() {
        // Chinese has only the `other` class, so the same entry has to serve every count.
        listOf(1, 2, 5, 21).forEach { count ->
            val zh = chinese.resources.getQuantityString(R.plurals.time_minutes_ago, count, count)
            assertTrue("missing count in \"$zh\"", zh.contains(count.toString()))
            assertTrue("not translated: $zh", zh.contains("分钟"))
        }

        assertEquals(
            "1 minute ago",
            english.resources.getQuantityString(R.plurals.time_minutes_ago, 1, 1),
        )
        assertEquals(
            "3 minutes ago",
            english.resources.getQuantityString(R.plurals.time_minutes_ago, 3, 3),
        )
    }

    @Test
    fun theLanguageNamesAreLeftInTheirOwnLanguage() {
        // A language picker that translates its own entries is unusable to anyone who
        // cannot already read the language it is currently in.
        assertEquals("English", chinese.getString(R.string.language_english))
        assertEquals("简体中文", english.getString(R.string.language_chinese))
    }

    @Test
    fun formatArgumentsSurviveTranslation() {
        // A dropped %1$s throws at runtime rather than at build time.
        assertTrue(chinese.getString(R.string.about_version, "9.9").contains("9.9"))
        assertTrue(chinese.getString(R.string.image_failed, "alt").contains("alt"))
        assertEquals("a · b", chinese.getString(R.string.card_meta, "a", "b"))
    }
}
