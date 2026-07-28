package com.mdview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mdview.R
import com.mdview.ui.theme.LocalSkin

/**
 * The centred icon-title-body block behind every "nothing here" screen.
 *
 * Shared rather than duplicated: this and `ListEmptyState` were the same forty lines with
 * the same magic numbers, and they had already drifted by one padding value.
 */
@Composable
fun CenteredNotice(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.AutoMirrored.Outlined.Article,
    actions: @Composable () -> Unit = {},
) {
    val skin = LocalSkin.current
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(skin.colors.accentSubtle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = skin.colors.accent,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = skin.colors.textPrimary,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = skin.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp).padding(top = 8.dp),
        )
        actions()
    }
}

/** Shown before any document is open, so the app never presents a blank page. */
@Composable
fun EmptyState(onOpen: () -> Unit, onNew: () -> Unit, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    CenteredNotice(
        title = stringResource(R.string.empty_title),
        body = stringResource(R.string.empty_body),
        modifier = modifier,
    ) {
        Button(
            onClick = onOpen,
            modifier = Modifier.padding(top = 24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = skin.colors.accent,
                contentColor = skin.colors.onAccent,
            ),
        ) {
            Text(stringResource(R.string.open_a_markdown_file))
        }
        TextButton(
            onClick = onNew,
            modifier = Modifier.padding(top = 4.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = skin.colors.accent),
        ) {
            Text(stringResource(R.string.start_a_new_document))
        }
    }
}
