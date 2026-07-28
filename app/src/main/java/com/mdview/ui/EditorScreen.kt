package com.mdview.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.mdview.ui.theme.LocalMonoTextStyle
import com.mdview.ui.theme.LocalSkin

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
    val skin = LocalSkin.current
    // Sizes come from LocalMonoTextStyle rather than being written here. Hardcoding them
    // overrode the scaled style, so the reading-size setting used to work in the preview
    // and do nothing whatsoever in the editor.
    val textStyle = LocalMonoTextStyle.current.copy(color = skin.colors.textPrimary)

    BasicTextField(
        state = state,
        modifier = modifier
            .fillMaxSize()
            .background(skin.colors.canvas)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        textStyle = textStyle,
        cursorBrush = SolidColor(skin.colors.accent),
        // MultiLine keeps Enter inserting a newline instead of closing the keyboard.
        lineLimits = TextFieldLineLimits.MultiLine(),
        scrollState = scrollState,
    )
}
