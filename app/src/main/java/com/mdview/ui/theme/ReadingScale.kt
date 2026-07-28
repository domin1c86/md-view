package com.mdview.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle

/**
 * How much to enlarge the document itself.
 *
 * Deliberately not applied to the whole [androidx.compose.material3.Typography]: the
 * user asked for bigger *reading* text, not a bigger top bar, bigger dialogs and bigger
 * navigation labels.
 */
val LocalReadingScale = compositionLocalOf { 1f }

/**
 * Scales the type used by the rendered document and the source editor.
 *
 * Everything here is in `sp`, so this multiplies with the system font scale rather than
 * replacing it. The product is capped: "large" on top of a device already set to its
 * largest accessibility size leaves a line barely wide enough for one word.
 */
@Composable
fun ReadingTypography(scale: Float, content: @Composable () -> Unit) {
    val capped = scale.coerceIn(MIN_SCALE, MAX_SCALE)
    val base = MaterialTheme.typography
    val scaled = remember(base, capped) {
        if (capped == 1f) {
            base
        } else {
            base.copy(
                displayLarge = base.displayLarge.scaleBy(capped),
                displayMedium = base.displayMedium.scaleBy(capped),
                displaySmall = base.displaySmall.scaleBy(capped),
                headlineLarge = base.headlineLarge.scaleBy(capped),
                headlineMedium = base.headlineMedium.scaleBy(capped),
                headlineSmall = base.headlineSmall.scaleBy(capped),
                titleLarge = base.titleLarge.scaleBy(capped),
                titleMedium = base.titleMedium.scaleBy(capped),
                titleSmall = base.titleSmall.scaleBy(capped),
                bodyLarge = base.bodyLarge.scaleBy(capped),
                bodyMedium = base.bodyMedium.scaleBy(capped),
                bodySmall = base.bodySmall.scaleBy(capped),
                labelLarge = base.labelLarge.scaleBy(capped),
                labelMedium = base.labelMedium.scaleBy(capped),
                labelSmall = base.labelSmall.scaleBy(capped),
            )
        }
    }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = scaled,
        shapes = MaterialTheme.shapes,
    ) {
        CompositionLocalProvider(
            LocalReadingScale provides capped,
            LocalTextStyle provides scaled.bodyLarge,
            content = content,
        )
    }
}

private fun TextStyle.scaleBy(factor: Float): TextStyle = copy(
    fontSize = fontSize * factor,
    lineHeight = lineHeight * factor,
)

private const val MIN_SCALE = 0.75f
private const val MAX_SCALE = 1.5f
