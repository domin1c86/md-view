package com.mdview.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * How much to enlarge the document itself.
 *
 * Deliberately not applied to the whole [androidx.compose.material3.Typography]: the
 * user asked for bigger *reading* text, not a bigger top bar, bigger dialogs and bigger
 * navigation labels.
 */
val LocalReadingScale = compositionLocalOf { 1f }

/**
 * Monospace text inside the document and the editor, already scaled.
 *
 * Exists because both used to hardcode a size -- 14 sp in the code block, 15 sp in the
 * editor -- which silently overrode the scaled style, so the reading-size setting worked
 * in prose and did nothing at all in code or in the editor.
 */
val LocalMonoTextStyle = compositionLocalOf {
    TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp)
}

/**
 * Scales the type used by the rendered document and the source editor.
 *
 * Everything here is in `sp`, so this multiplies with the system font scale rather than
 * replacing it. The product is capped: "large" on top of a device already set to its
 * largest accessibility size leaves a line barely wide enough for one word.
 *
 * **This re-invokes [MaterialTheme], so anything passed to it must be re-forwarded here.**
 * `colorScheme` and `shapes` are, below. CompositionLocals -- [LocalSkin] included -- are
 * not, and must not be: they propagate on their own, and forwarding one would pin it to
 * whatever it happened to be at this point in the tree.
 */
@Composable
fun ReadingTypography(scale: Float, content: @Composable () -> Unit) {
    val capped = scale.coerceIn(MIN_SCALE, MAX_SCALE)
    val base = MaterialTheme.typography
    val monoScale = LocalSkin.current.type.monoScale
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

    val mono = remember(scaled, monoScale) {
        scaled.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = scaled.bodyLarge.fontSize * monoScale,
            lineHeight = scaled.bodyLarge.lineHeight * monoScale,
            letterSpacing = TextUnit.Unspecified,
        )
    }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = scaled,
        shapes = MaterialTheme.shapes,
    ) {
        CompositionLocalProvider(
            LocalReadingScale provides capped,
            LocalTextStyle provides scaled.bodyLarge,
            LocalMonoTextStyle provides mono,
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
