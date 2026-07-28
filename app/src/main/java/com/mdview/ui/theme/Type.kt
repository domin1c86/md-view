package com.mdview.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The type scale, adjusted by a skin's [SkinType].
 *
 * Everything is expressed in `sp`, so it compounds with the accessibility font scale
 * rather than replacing it, and the reading-size setting multiplies on top again inside
 * [ReadingTypography]. That is also why [SkinType] carries multipliers rather than
 * absolute sizes: a skin able to pin body text at 11 sp would silently defeat both.
 */
internal fun mdViewTypography(type: SkinType): Typography {
    val base = Typography()
    val heading = FontWeight(type.headingWeight.coerceIn(100, 900))
    val body = type.bodyScale.coerceIn(MIN_BODY_SCALE, MAX_BODY_SCALE)
    val tracking = type.tracking.coerceIn(MIN_TRACKING, MAX_TRACKING).em

    // Large type looks loose at Material's default tracking once it is set bold; pulling
    // it in is most of what makes big headings read as deliberate rather than default.
    fun TextStyle.asHeading() = copy(fontWeight = heading, letterSpacing = HEADING_TRACKING)

    fun TextStyle.asBody() = copy(
        fontSize = fontSize * body,
        lineHeight = lineHeight * body,
        letterSpacing = tracking,
    )

    return base.copy(
        displayLarge = base.displayLarge.asHeading(),
        displayMedium = base.displayMedium.asHeading(),
        displaySmall = base.displaySmall.asHeading(),
        headlineLarge = base.headlineLarge.asHeading(),
        headlineMedium = base.headlineMedium.asHeading(),
        headlineSmall = base.headlineSmall.asHeading(),
        titleLarge = base.titleLarge.asHeading(),
        titleMedium = base.titleMedium.copy(fontWeight = heading),
        titleSmall = base.titleSmall.copy(fontWeight = heading),
        // 16/26 rather than Material's 16/24. The extra leading is the single biggest
        // difference between a screen that reads like a document and one that does not.
        bodyLarge = base.bodyLarge.merge(ReadingBody).asBody(),
        bodyMedium = base.bodyMedium.asBody(),
        bodySmall = base.bodySmall.asBody(),
    )
}

private val ReadingBody = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 26.sp,
)

private val HEADING_TRACKING = (-0.015f).em

private const val MIN_BODY_SCALE = 0.85f
private const val MAX_BODY_SCALE = 1.3f
private const val MIN_TRACKING = -0.05f
private const val MAX_TRACKING = 0.1f
