package com.mdview.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A plain-text view of the Markdown source. Deliberately unadorned: no toolbar,
 * no autocomplete, no syntax highlighting -- just the characters in the file.
 *
 * [scrollState] is passed in rather than remembered here so the caret stays put when
 * the user flips to the preview and back.
 */
@Composable
fun EditorScreen(
    state: TextFieldState,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val textStyle = LocalTextStyle.current.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )

    BasicTextField(
        state = state,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        // MultiLine keeps Enter inserting a newline instead of closing the keyboard.
        lineLimits = TextFieldLineLimits.MultiLine(),
        scrollState = scrollState,
    )
}
