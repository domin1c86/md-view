package com.mdview.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Body text is tuned for long-form reading: slightly larger and looser than the
// Material default, which is sized for UI labels rather than prose.
private val ReadingBody = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 26.sp,
)

internal val MdViewTypography = Typography().let { base ->
    base.copy(
        bodyLarge = base.bodyLarge.merge(ReadingBody),
    )
}
