package com.mdview.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colours a skin controls.
 *
 * Material 3's `ColorScheme` cannot express most of what a Markdown reader needs a theme
 * to own -- a code block, a blockquote bar and a table border all end up derived from
 * `primary` or `surfaceVariant` with an alpha guess, which is exactly what this replaces.
 *
 * The four surface values form a ladder rather than a set, and the order matters:
 * [canvas] is the deepest layer, [surfaceSunken] the recessed one. A skin that collapses
 * two rungs to the same colour is legal -- Paper does, for surface and surfaceRaised --
 * but a skin that inverts them will look wrong everywhere at once.
 *
 * Pure Kotlin apart from [Color], which is a value class over a ULong, so this whole
 * model is testable on the JVM without an emulator.
 */
data class SkinColors(
    /** The window background, behind everything including the bars. */
    val canvas: Color,
    /** Cards, list rows, bars. Sits one step above [canvas]. */
    val surface: Color,
    /** Menus, dialogs, pressed cards. The only rung allowed to cast a shadow. */
    val surfaceRaised: Color,
    /** Recessed fills: code blocks, table bodies, the editor's background. */
    val surfaceSunken: Color,

    /** The one colour that carries the skin's identity. Controls, indicators, focus. */
    val accent: Color,
    /** Text and icons drawn on top of [accent]. */
    val onAccent: Color,
    /** A wash of [accent] used behind selected rows and pressed links. */
    val accentSubtle: Color,

    /** Body copy and headings. */
    val textPrimary: Color,
    /** Supporting copy: excerpts, setting descriptions, list markers. */
    val textSecondary: Color,
    /** The quietest readable text: timestamps, captions, language chips. */
    val textMuted: Color,

    /** Hairlines around cards and code blocks. */
    val border: Color,
    /** Rules between rows and under headings. Usually quieter than [border]. */
    val divider: Color,

    /** Link text. */
    val link: Color,
    /** Link text while the touch is down. */
    val linkPressed: Color,

    /** Monospace text inside code spans and code blocks. */
    val code: Color,
    /** The fill behind code spans and code blocks. */
    val codeBackground: Color,

    /** The vertical rule down the left of a blockquote. */
    val quoteBar: Color,
    /** Text inside a blockquote. */
    val quoteText: Color,

    /** The fill behind a table's header row. */
    val tableHeader: Color,
    /** Table rules, both between rows and between columns. */
    val tableBorder: Color,

    /** Errors, read-only notices, destructive actions. */
    val danger: Color,
    /** Confirmations. Currently only the "Saved" snackbar. */
    val success: Color,
    /** Text selection and the editor's cursor. */
    val selection: Color,
)

/**
 * Corner radii, in dp.
 *
 * [small] is for chips and inline fills, [medium] for cards and code blocks, [large] for
 * dialogs, menus and the navigation rail's selection pill.
 */
data class SkinShape(
    val small: Int,
    val medium: Int,
    val large: Int,
)

/**
 * Type adjustments a skin may make.
 *
 * Deliberately multipliers rather than absolute sizes: every size in the app is in `sp`
 * so it already compounds with the accessibility font scale, and the reading-size setting
 * multiplies on top of that again. A skin that could set absolute sizes would be able to
 * quietly defeat both.
 */
data class SkinType(
    /** Scales the document and editor text. Clamped by [ReadingTypography]'s own bounds. */
    val bodyScale: Float,
    /** Scales monospace text relative to body text. */
    val monoScale: Float,
    /** Font weight for headings, 100..900. */
    val headingWeight: Int,
    /** Extra letter spacing for body text, in em. Negative tightens. */
    val tracking: Float,
)

/**
 * One complete theme.
 *
 * [id] is restricted to `[a-z0-9][a-z0-9-]{0,63}` by `SkinCodec`, and that restriction is
 * load-bearing rather than cosmetic: ids are written into `settings.txt`, whose `key=value`
 * format has no escaping at all by design. Widening the charset would silently make a
 * skin name able to corrupt every other preference.
 */
data class Skin(
    val id: String,
    val name: String,
    val author: String?,
    val dark: Boolean,
    val colors: SkinColors,
    val shape: SkinShape,
    val type: SkinType,
) {
    /** Exists so [fromColorScheme] has somewhere to hang. */
    companion object
}

/**
 * The active skin.
 *
 * Defaults to a real skin rather than `error(...)` so `@Preview` and any composable that
 * ends up outside [MdViewTheme] still draw something sensible instead of crashing.
 *
 * Static because the skin changes rarely and is read almost everywhere -- an invalidation
 * of the whole tree on the rare change is cheaper than tracking every read site.
 */
val LocalSkin = staticCompositionLocalOf { BuiltInSkins.Paper }
