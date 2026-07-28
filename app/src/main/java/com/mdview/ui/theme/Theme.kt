package com.mdview.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
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

    CompositionLocalProvider(LocalSkin provides effective) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}
