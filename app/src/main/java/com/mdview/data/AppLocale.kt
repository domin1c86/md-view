package com.mdview.data

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Applies the in-app language, on both sides of the API 33 divide.
 *
 * From Android 13 the platform owns per-app language: [LocaleManager] persists the
 * choice, restarts the Activity and surfaces the setting in system Settings. Below that
 * there is no such API — and adding `androidx.appcompat` for
 * `AppCompatDelegate.setApplicationLocales` would mean switching `MainActivity` to
 * `AppCompatActivity` and the manifest to an AppCompat theme — so the Activity's own
 * context is wrapped instead.
 */
object AppLocale {

    private val hasPlatformSupport: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Wraps [base] so resources resolve in the chosen language.
     *
     * Only called below API 33 — above it the platform has already applied the locale,
     * and wrapping again would layer one override on top of another.
     */
    fun wrap(base: Context, choice: LanguageChoice): Context {
        if (hasPlatformSupport) return base
        val locale = choice.toLocale() ?: return base

        // Starting from the existing configuration rather than a bare Configuration():
        // a fresh one defaults fontScale to 1.0 and updateFrom copies it across, which
        // silently discards the user's accessibility text size.
        val config = Configuration(base.resources.configuration).apply {
            setLocales(LocaleList(locale))
        }

        // Below 33 the platform default is untouched, so String.format, NumberFormat and
        // DateFormat would all keep using the system language while the UI does not.
        Locale.setDefault(locale)
        return base.createConfigurationContext(config)
    }

    /**
     * Tells the platform about a language change on API 33+, where it -- not this app --
     * is the source of truth. No-op below that; the caller recreates the Activity.
     */
    fun apply(context: Context, choice: LanguageChoice) {
        if (!hasPlatformSupport) return
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        manager.applicationLocales = choice.tag
            ?.let { LocaleList.forLanguageTags(it) }
            ?: LocaleList.getEmptyLocaleList()
    }

    /**
     * The language actually in force.
     *
     * On API 33+ the user can change it from system Settings, so asking the platform is
     * the only way to keep the radio buttons honest; mirroring it into `settings.txt`
     * would go stale the moment they did.
     */
    fun current(context: Context, stored: LanguageChoice): LanguageChoice {
        if (!hasPlatformSupport) return stored
        val manager = context.getSystemService(LocaleManager::class.java)
            ?: return stored
        val tag = manager.applicationLocales.takeUnless { it.isEmpty }?.get(0)?.language
            ?: return LanguageChoice.System
        return LanguageChoice.entries.firstOrNull { choice ->
            choice.tag?.substringBefore('-') == tag
        } ?: LanguageChoice.System
    }

    private fun LanguageChoice.toLocale(): Locale? = tag?.let(Locale::forLanguageTag)
}
