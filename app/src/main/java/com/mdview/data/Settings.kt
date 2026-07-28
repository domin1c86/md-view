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
    val dynamicColor: Boolean = true,
    val remoteImages: RemoteImagePolicy = RemoteImagePolicy.Always,
    val readingSize: ReadingSize = ReadingSize.Medium,
)
