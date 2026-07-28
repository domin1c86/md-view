package com.mdview.data

/** Which colour scheme to use, regardless of what the system is doing. */
enum class ThemeChoice { System, Light, Dark }

/** UI language. [System] follows the device; the rest force one locale. */
enum class LanguageChoice(val tag: String?) {
    System(null),
    English("en"),
    Chinese("zh-Hans"),
}

/**
 * When a document may fetch an image from the network.
 *
 * Note this is about *metered* connections rather than Wi-Fi: a phone hotspot reports
 * itself as Wi-Fi but still costs money, and a dock's Ethernet is unmetered but is not
 * Wi-Fi at all.
 */
enum class RemoteImagePolicy { Never, Unmetered, Always }

/** Scales the rendered document and the source editor, not the app's chrome. */
enum class ReadingSize(val scale: Float) {
    Small(0.875f),
    Medium(1f),
    Large(1.25f),
}

data class Settings(
    val theme: ThemeChoice = ThemeChoice.System,
    val language: LanguageChoice = LanguageChoice.System,
    /**
     * Off by default, unlike before skins existed.
     *
     * Material You overrides the chosen skin wholesale, so leaving it on would mean a
     * fresh install on Android 12+ never showed the skin it says is selected. Installs
     * that already wrote `dynamicColor=true` keep it -- only the default moved.
     */
    val dynamicColor: Boolean = false,
    val remoteImages: RemoteImagePolicy = RemoteImagePolicy.Always,
    val readingSize: ReadingSize = ReadingSize.Medium,
    /** Skin used when the resolved mode is light. See `docs/SKINS.md`. */
    val lightSkinId: String = "paper",
    /** Skin used when the resolved mode is dark. */
    val darkSkinId: String = "ink",
)

/**
 * Whether this choice means a dark scheme, given what the system is currently doing.
 *
 * Lives here rather than in the Activity because three callers need it: the window
 * chrome before `onCreate`, the theme during composition, and the settings panel, which
 * has to know which of the two skin pickers is the live one.
 */
fun ThemeChoice.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeChoice.System -> systemDark
    ThemeChoice.Light -> false
    ThemeChoice.Dark -> true
}
