package com.mdview.ui.theme

import android.os.Build
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Applies [skin] to both Material 3 and the app's own tokens.
 *
 * When [dynamicColor] is on and the platform supports it, the wallpaper wins -- but the
 * skin is rebuilt from the wallpaper scheme rather than simply bypassed, so a code block
 * or a table follows Material You too. Skipping that step leaves half the screen themed
 * from the wallpaper and the other half from a skin the user thought they had overridden.
 *
 * [skin] still supplies shape and type in that case, since a wallpaper implies neither.
 *
 * Motion is the one thing here that does *not* come from the skin -- see [MdViewMotion]
 * for why -- but it is applied in the same place, so there is a single point where the
 * whole design system reaches Material.
 */
@Composable
fun MdViewTheme(
    skin: Skin,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val effective = remember(skin, dynamicColor) {
        if (!dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            skin
        } else {
            val scheme = if (skin.dark) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
            Skin.fromColorScheme(scheme, skin.dark, template = skin)
        }
    }

    val colorScheme = remember(effective) { effective.toColorScheme() }
    val shapes = remember(effective) { effective.toShapes() }
    val typography = remember(effective) { mdViewTypography(effective.type) }
    val ripple = remember(effective) { effective.toRippleConfiguration() }

    CompositionLocalProvider(LocalSkin provides effective) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
        ) {
            // Inside the theme rather than beside it: this is press feedback for Material's
            // own components, so it belongs where they can see it. One provider is all it
            // takes -- every `clickable`, card, list row and icon button in the app reads
            // the same local, with nothing to remember at the call sites.
            CompositionLocalProvider(
                LocalRippleConfiguration provides ripple,
                content = content,
            )
        }
    }
}

/**
 * Press, hover and focus feedback, drawn in the skin's accent instead of Material's
 * default wash of `onSurface`.
 *
 * The alphas differ by mode because the accent does. On a light skin it is a dark,
 * saturated colour on a pale surface and shows up readily; on a dark skin it is a light
 * colour that has to work harder against a near-black background. Using one set of alphas
 * for both makes the ripple shouty in light mode and invisible in dark.
 */
private fun Skin.toRippleConfiguration(): RippleConfiguration = RippleConfiguration(
    color = colors.accent,
    rippleAlpha = if (dark) {
        RippleAlpha(
            draggedAlpha = 0.18f,
            focusedAlpha = 0.14f,
            hoveredAlpha = 0.08f,
            pressedAlpha = 0.14f,
        )
    } else {
        RippleAlpha(
            draggedAlpha = 0.14f,
            focusedAlpha = 0.10f,
            hoveredAlpha = 0.06f,
            pressedAlpha = 0.10f,
        )
    },
)
