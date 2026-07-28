package com.mdview.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * Projects a [Skin] onto Material 3's `ColorScheme`.
 *
 * This exists so the stock components -- `Switch`, `RadioButton`, `NavigationBar`,
 * `FloatingActionButton`, `Snackbar`, `DropdownMenu`, `AlertDialog` -- follow the skin
 * without every call site naming a token by hand. Skin-specific surfaces that M3 has no
 * slot for (code, quote bar, table rules) still come from [LocalSkin].
 *
 * **Every slot is filled deliberately.** The palette this replaced set 12 of them and let
 * the rest fall through to the M3 baseline, which is why it always looked half-applied:
 * a themed app with a stock purple `tertiary` and a stock `surfaceContainerHigh` under
 * its menus.
 */
fun Skin.toColorScheme(): ColorScheme {
    val c = colors
    val base = if (dark) darkColorScheme() else lightColorScheme()

    return base.copy(
        primary = c.accent,
        onPrimary = c.onAccent,
        primaryContainer = c.accentSubtle,
        onPrimaryContainer = c.textPrimary,
        inversePrimary = c.accent,

        secondary = c.accent,
        onSecondary = c.onAccent,
        // The NavigationBar/NavigationRail selection pill reads this one.
        secondaryContainer = c.accentSubtle,
        onSecondaryContainer = c.textPrimary,

        tertiary = c.accent,
        onTertiary = c.onAccent,
        tertiaryContainer = c.accentSubtle,
        onTertiaryContainer = c.textPrimary,

        background = c.canvas,
        onBackground = c.textPrimary,
        surface = c.surface,
        onSurface = c.textPrimary,
        surfaceVariant = c.surfaceSunken,
        onSurfaceVariant = c.textSecondary,
        surfaceTint = c.accent,
        inverseSurface = c.textPrimary,
        inverseOnSurface = c.canvas,

        error = c.danger,
        // The danger colour is dark on light skins and light on dark ones, so what reads
        // on top of it flips with the skin rather than being a fixed white.
        onError = if (dark) c.canvas else Color.White,
        errorContainer = lerp(c.surface, c.danger, 0.16f),
        onErrorContainer = c.textPrimary,

        outline = c.border,
        outlineVariant = c.divider,
        scrim = Color.Black,

        // The surface ladder, which is what gives menus and sheets their separation.
        surfaceBright = if (dark) lerp(c.surface, c.textPrimary, 0.08f) else c.surface,
        surfaceDim = if (dark) c.canvas else lerp(c.canvas, c.textPrimary, 0.06f),
        surfaceContainerLowest = c.canvas,
        surfaceContainerLow = c.surfaceSunken,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.surfaceRaised,
        surfaceContainerHighest = lerp(c.surfaceRaised, c.textPrimary, 0.04f),
    )
}

/** Material 3's five shape slots, driven by the skin's three radii. */
fun Skin.toShapes(): Shapes = Shapes(
    extraSmall = RoundedCornerShape((shape.small / 2).coerceAtLeast(2).dp),
    small = RoundedCornerShape(shape.small.dp),
    medium = RoundedCornerShape(shape.medium.dp),
    large = RoundedCornerShape(shape.large.dp),
    extraLarge = RoundedCornerShape((shape.large * 1.4f).toInt().dp),
)

/**
 * Rebuilds a skin's tokens from a Material You scheme.
 *
 * Without this, turning on dynamic colour would recolour the chrome from the wallpaper
 * while code blocks, tables and blockquotes stayed on the previous skin -- the two
 * halves of the same screen themed by different sources. [template] supplies everything
 * the wallpaper cannot imply, which is the shape and type scale.
 */
fun Skin.Companion.fromColorScheme(
    scheme: ColorScheme,
    dark: Boolean,
    template: Skin,
): Skin = template.copy(
    id = if (dark) DYNAMIC_DARK_ID else DYNAMIC_LIGHT_ID,
    name = template.name,
    dark = dark,
    colors = SkinColors(
        canvas = scheme.background,
        surface = scheme.surfaceContainer,
        surfaceRaised = scheme.surfaceContainerHigh,
        surfaceSunken = scheme.surfaceContainerLow,
        accent = scheme.primary,
        onAccent = scheme.onPrimary,
        accentSubtle = scheme.secondaryContainer,
        textPrimary = scheme.onSurface,
        textSecondary = scheme.onSurfaceVariant,
        // M3 has no third text rung, so it is derived by fading towards the surface.
        textMuted = lerp(scheme.onSurfaceVariant, scheme.surface, 0.3f),
        border = scheme.outline,
        divider = scheme.outlineVariant,
        link = scheme.primary,
        linkPressed = lerp(scheme.primary, scheme.onSurface, 0.25f),
        code = scheme.onSurfaceVariant,
        codeBackground = scheme.surfaceContainerLow,
        quoteBar = scheme.primary,
        quoteText = scheme.onSurfaceVariant,
        tableHeader = scheme.surfaceContainerLow,
        tableBorder = scheme.outlineVariant,
        danger = scheme.error,
        success = template.colors.success,
        selection = scheme.secondaryContainer,
    ),
)

internal const val DYNAMIC_LIGHT_ID = "dynamic-light"
internal const val DYNAMIC_DARK_ID = "dynamic-dark"
