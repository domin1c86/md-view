package com.mdview.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The skins that ship with the app.
 *
 * Every one of these clears the contrast floors documented in `docs/SKINS.md` -- body
 * text at 4.5:1 and secondary or status colours at 3:1, against *all four* surface rungs
 * rather than just one. `BuiltInSkinsTest` asserts that in code, so a later palette tweak
 * cannot quietly regress it.
 *
 * Nord and Solarized deliberately deviate from their canonical values in two places:
 * Nord's `#BF616A` red scores 2.46 against its own surface and Solarized's `#268BD2`
 * scores 3.68 under white text. Both palettes were designed for terminals, where nothing
 * has to pass WCAG. The adjusted values stay recognisable but are actually readable.
 */
object BuiltInSkins {

    val Paper = Skin(
        id = "paper",
        name = "Paper",
        author = null,
        dark = false,
        shape = DefaultShape,
        type = DefaultType,
        colors = SkinColors(
            canvas = Color(0xFFF4F5F7),
            surface = Color(0xFFFFFFFF),
            surfaceRaised = Color(0xFFFFFFFF),
            surfaceSunken = Color(0xFFF0F1F4),
            accent = Color(0xFF2F6FEB),
            onAccent = Color(0xFFFFFFFF),
            accentSubtle = Color(0xFFE4ECFC),
            textPrimary = Color(0xFF12141A),
            textSecondary = Color(0xFF4A5160),
            textMuted = Color(0xFF6E7684),
            border = Color(0xFFDADEE6),
            divider = Color(0xFFEDEFF3),
            link = Color(0xFF1B62D6),
            linkPressed = Color(0xFF12459C),
            code = Color(0xFFA62D66),
            codeBackground = Color(0xFFF0F1F4),
            quoteBar = Color(0xFF2F6FEB),
            quoteText = Color(0xFF4A5160),
            tableHeader = Color(0xFFF0F1F4),
            tableBorder = Color(0xFFD6DBE4),
            danger = Color(0xFFC02B2B),
            success = Color(0xFF197A4B),
            selection = Color(0xFFCFE0FB),
        ),
    )

    val Ink = Skin(
        id = "ink",
        name = "Ink",
        author = null,
        dark = true,
        shape = DefaultShape,
        type = DefaultType,
        colors = SkinColors(
            canvas = Color(0xFF101216),
            surface = Color(0xFF171A20),
            surfaceRaised = Color(0xFF1F232B),
            surfaceSunken = Color(0xFF0C0E12),
            accent = Color(0xFF6E9BFF),
            onAccent = Color(0xFF0A0D14),
            accentSubtle = Color(0xFF1B2436),
            textPrimary = Color(0xFFE6E9EF),
            textSecondary = Color(0xFFA8B0BF),
            textMuted = Color(0xFF7C8494),
            border = Color(0xFF262B34),
            divider = Color(0xFF1E222A),
            link = Color(0xFF7FB0FF),
            linkPressed = Color(0xFFA9C8FF),
            code = Color(0xFFE4A8CE),
            codeBackground = Color(0xFF0C0E12),
            quoteBar = Color(0xFF6E9BFF),
            quoteText = Color(0xFFA8B0BF),
            tableHeader = Color(0xFF1F232B),
            tableBorder = Color(0xFF343B47),
            danger = Color(0xFFFF6B6B),
            success = Color(0xFF43C08A),
            selection = Color(0xFF2A3A5C),
        ),
    )

    /** Document-first, in the spirit of modern WPS: white paper on a cool grey desk. */
    val Cobalt = Skin(
        id = "cobalt",
        name = "Cobalt",
        author = null,
        dark = false,
        shape = DefaultShape,
        type = DefaultType,
        colors = SkinColors(
            canvas = Color(0xFFEEF2F8),
            surface = Color(0xFFFFFFFF),
            surfaceRaised = Color(0xFFFFFFFF),
            surfaceSunken = Color(0xFFE8EEF7),
            accent = Color(0xFF1462C8),
            onAccent = Color(0xFFFFFFFF),
            accentSubtle = Color(0xFFDCE8F8),
            textPrimary = Color(0xFF0F1B2D),
            textSecondary = Color(0xFF3D5069),
            textMuted = Color(0xFF64748B),
            border = Color(0xFFCBD8E9),
            divider = Color(0xFFE4EBF4),
            link = Color(0xFF0B57B8),
            linkPressed = Color(0xFF08408A),
            code = Color(0xFFA32F6B),
            codeBackground = Color(0xFFE8EEF7),
            quoteBar = Color(0xFF1462C8),
            quoteText = Color(0xFF3D5069),
            tableHeader = Color(0xFFE8EEF7),
            tableBorder = Color(0xFFC6D4E8),
            danger = Color(0xFFC2371F),
            success = Color(0xFF10714B),
            selection = Color(0xFFC7DCF6),
        ),
    )

    /** Warm and low-contrast-blue, for long reading sessions. */
    val Sepia = Skin(
        id = "sepia",
        name = "Sepia",
        author = null,
        dark = false,
        shape = DefaultShape,
        type = DefaultType,
        colors = SkinColors(
            canvas = Color(0xFFF2EAD9),
            surface = Color(0xFFFBF5E9),
            surfaceRaised = Color(0xFFFFFBF2),
            surfaceSunken = Color(0xFFEAE0CB),
            accent = Color(0xFF9A5B27),
            onAccent = Color(0xFFFFFFFF),
            accentSubtle = Color(0xFFEDDFC9),
            textPrimary = Color(0xFF2A2114),
            textSecondary = Color(0xFF5A4A33),
            textMuted = Color(0xFF7A6848),
            border = Color(0xFFDED0B4),
            divider = Color(0xFFE8DCC4),
            link = Color(0xFF8A4B1F),
            linkPressed = Color(0xFF6B3915),
            code = Color(0xFF8A3A4A),
            codeBackground = Color(0xFFEAE0CB),
            quoteBar = Color(0xFF9A5B27),
            quoteText = Color(0xFF5A4A33),
            tableHeader = Color(0xFFEAE0CB),
            tableBorder = Color(0xFFD4C29C),
            danger = Color(0xFFA33224),
            success = Color(0xFF4A6B2F),
            selection = Color(0xFFE0CFA8),
        ),
    )

    /** Near-black with raised grey rungs and a blurple accent. */
    val Midnight = Skin(
        id = "midnight",
        name = "Midnight",
        author = null,
        dark = true,
        shape = DefaultShape,
        type = DefaultType,
        colors = SkinColors(
            canvas = Color(0xFF1A1B1E),
            surface = Color(0xFF232428),
            surfaceRaised = Color(0xFF2B2D31),
            surfaceSunken = Color(0xFF111214),
            accent = Color(0xFF5865F2),
            onAccent = Color(0xFFFFFFFF),
            accentSubtle = Color(0xFF2B2F49),
            textPrimary = Color(0xFFF2F3F5),
            textSecondary = Color(0xFFB5BAC1),
            textMuted = Color(0xFF949BA4),
            border = Color(0xFF36383E),
            divider = Color(0xFF26282C),
            link = Color(0xFF00A8FC),
            linkPressed = Color(0xFF4DC4FF),
            code = Color(0xFFE9A3C9),
            codeBackground = Color(0xFF2B2D31),
            quoteBar = Color(0xFF4E5058),
            quoteText = Color(0xFFB5BAC1),
            tableHeader = Color(0xFF2B2D31),
            tableBorder = Color(0xFF464851),
            danger = Color(0xFFF23F43),
            success = Color(0xFF23A55A),
            selection = Color(0xFF3C4270),
        ),
    )

    val Nord = Skin(
        id = "nord",
        name = "Nord",
        author = null,
        dark = true,
        shape = DefaultShape,
        type = DefaultType,
        colors = SkinColors(
            canvas = Color(0xFF2E3440),
            surface = Color(0xFF3B4252),
            surfaceRaised = Color(0xFF434C5E),
            surfaceSunken = Color(0xFF272C36),
            accent = Color(0xFF88C0D0),
            onAccent = Color(0xFF2E3440),
            accentSubtle = Color(0xFF3B4A56),
            textPrimary = Color(0xFFECEFF4),
            textSecondary = Color(0xFFD8DEE9),
            textMuted = Color(0xFFA8B3C4),
            border = Color(0xFF4C566A),
            divider = Color(0xFF434C5E),
            // Canonical Nord uses #8FBCBB, which drops to 4.14:1 on the raised rung.
            link = Color(0xFFA3CFCE),
            linkPressed = Color(0xFFB4D6D5),
            code = Color(0xFFEBCB8B),
            codeBackground = Color(0xFF272C36),
            quoteBar = Color(0xFF81A1C1),
            quoteText = Color(0xFFD8DEE9),
            tableHeader = Color(0xFF434C5E),
            tableBorder = Color(0xFF57627A),
            // Canonical #BF616A scores 2.46:1 on Nord's own surface.
            danger = Color(0xFFD9848D),
            success = Color(0xFFA3BE8C),
            selection = Color(0xFF4C566A),
        ),
    )

    val SolarizedLight = Skin(
        id = "solarized-light",
        name = "Solarized Light",
        author = null,
        dark = false,
        shape = DefaultShape,
        type = DefaultType,
        colors = SkinColors(
            canvas = Color(0xFFFDF6E3),
            surface = Color(0xFFFFFBF0),
            surfaceRaised = Color(0xFFFFFDF7),
            surfaceSunken = Color(0xFFEEE8D5),
            // Canonical #268BD2 carries white text at only 3.68:1.
            accent = Color(0xFF186FA8),
            onAccent = Color(0xFFFFFFFF),
            accentSubtle = Color(0xFFDDE7EC),
            textPrimary = Color(0xFF073642),
            textSecondary = Color(0xFF4E6469),
            textMuted = Color(0xFF6B7A7E),
            border = Color(0xFFDDD6C1),
            divider = Color(0xFFE8E1CE),
            link = Color(0xFF175E93),
            linkPressed = Color(0xFF0F4670),
            code = Color(0xFFA32A63),
            codeBackground = Color(0xFFEEE8D5),
            quoteBar = Color(0xFF186FA8),
            quoteText = Color(0xFF4E6469),
            tableHeader = Color(0xFFEEE8D5),
            tableBorder = Color(0xFFD2C9AE),
            danger = Color(0xFFB3392F),
            success = Color(0xFF5F6E00),
            selection = Color(0xFFD8E3E8),
        ),
    )

    val SolarizedDark = Skin(
        id = "solarized-dark",
        name = "Solarized Dark",
        author = null,
        dark = true,
        shape = DefaultShape,
        type = DefaultType,
        colors = SkinColors(
            canvas = Color(0xFF002B36),
            surface = Color(0xFF073642),
            surfaceRaised = Color(0xFF0E4551),
            surfaceSunken = Color(0xFF00212B),
            accent = Color(0xFF3AA0E0),
            onAccent = Color(0xFF00232C),
            accentSubtle = Color(0xFF0C3F52),
            textPrimary = Color(0xFFEEE8D5),
            textSecondary = Color(0xFFB9C4C4),
            textMuted = Color(0xFF93A1A1),
            border = Color(0xFF14505E),
            divider = Color(0xFF0D4351),
            link = Color(0xFF69B7E8),
            linkPressed = Color(0xFF9BD0F2),
            code = Color(0xFFE08CB4),
            codeBackground = Color(0xFF00212B),
            quoteBar = Color(0xFF3AA0E0),
            quoteText = Color(0xFFB9C4C4),
            tableHeader = Color(0xFF0E4551),
            tableBorder = Color(0xFF1D6373),
            danger = Color(0xFFEF5F5A),
            success = Color(0xFF8FB33A),
            selection = Color(0xFF14505E),
        ),
    )

    /** Built-ins in the order the settings gallery shows them, light first. */
    val all: List<Skin> = listOf(
        Paper, Cobalt, Sepia, SolarizedLight,
        Ink, Midnight, Nord, SolarizedDark,
    )

    /** The default for each mode, and the fallback when an id no longer resolves. */
    fun default(dark: Boolean): Skin = if (dark) Ink else Paper

    fun byId(id: String): Skin? = all.firstOrNull { it.id == id }
}

/**
 * Rounder than Material 3's defaults on purpose -- 16 dp on cards and 20 dp on menus is
 * what reads as current rather than as 2021 Material You.
 */
internal val DefaultShape = SkinShape(small = 8, medium = 16, large = 20)

internal val DefaultType = SkinType(
    bodyScale = 1f,
    monoScale = 0.9f,
    headingWeight = 700,
    tracking = 0f,
)
