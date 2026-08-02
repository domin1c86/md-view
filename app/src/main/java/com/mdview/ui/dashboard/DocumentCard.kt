package com.mdview.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mdview.R
import com.mdview.data.LibraryEntry
import com.mdview.ui.SkinDropdownMenu
import com.mdview.ui.theme.LocalSkin

/**
 * Stable handles for the instrumented tests.
 *
 * Labels alone stopped being enough here: a tab's name also appears in the top bar, and
 * every card carries an identically-described star, so text and content-description
 * lookups match more than one node.
 */
object DashboardTags {
    const val CARD_LIST = "dashboard:cards"
    const val DRAFT_CARD = "dashboard:draft"
    fun card(uri: String) = "dashboard:card:$uri"
    fun star(uri: String) = "dashboard:star:$uri"
    fun tab(name: String) = "dashboard:tab:$name"
}

/**
 * One document in the list: its heading, an excerpt of the body, and where it came from.
 *
 * [unavailable] means the URI grant is gone — the file may still exist, but this app can
 * no longer reach it, so the card says so rather than failing on tap.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentCard(
    entry: LibraryEntry,
    unavailable: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val skin = LocalSkin.current

    Card(
        modifier = modifier.fillMaxWidth().testTag(DashboardTags.card(entry.uri)),
        shape = RoundedCornerShape(skin.shape.medium.dp),
        colors = CardDefaults.cardColors(containerColor = skin.colors.surface),
        // A hairline instead of a shadow: shadows read as 2021 Material, and on a dark
        // skin they are invisible anyway, which is where the surface ladder earns its keep.
        border = BorderStroke(1.dp, skin.colors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(
                    onClick = { if (unavailable) menuOpen = true else onOpen() },
                    onLongClick = { menuOpen = true },
                )
                .padding(start = 16.dp, top = 14.dp, end = 4.dp, bottom = 14.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Article,
                contentDescription = null,
                tint = skin.colors.textMuted,
                modifier = Modifier.padding(top = 2.dp, end = 12.dp).size(20.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    // Dimmed rather than hidden: the user still recognises the document,
                    // and can remove it deliberately instead of wondering where it went.
                    .alpha(if (unavailable) 0.5f else 1f),
            ) {
                Text(
                    text = entry.heading,
                    style = MaterialTheme.typography.titleSmall,
                    color = skin.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.excerpt.isNotBlank()) {
                    Text(
                        text = entry.excerpt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = skin.colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text(
                    text = stringResource(
                        R.string.card_meta,
                        entry.displayName,
                        relativeTime(entry.lastOpened),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = skin.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                CardNotice(entry, unavailable)
            }

            Box {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.testTag(DashboardTags.star(entry.uri)),
                ) {
                    Icon(
                        imageVector = if (entry.isFavorite) {
                            Icons.Filled.Star
                        } else {
                            Icons.Outlined.StarBorder
                        },
                        contentDescription = stringResource(
                            if (entry.isFavorite) {
                                R.string.remove_from_favorites
                            } else {
                                R.string.add_to_favorites
                            }
                        ),
                        tint = if (entry.isFavorite) {
                            skin.colors.accent
                        } else {
                            skin.colors.textMuted
                        },
                    )
                }

                SkinDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (entry.isFavorite) {
                                        R.string.remove_from_favorites
                                    } else {
                                        R.string.add_to_favorites
                                    }
                                )
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onToggleFavorite()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove_from_list)) },
                        onClick = {
                            menuOpen = false
                            onForget()
                        },
                    )
                }
            }
        }
    }
}

/** The one-line caveat under a card, when there is one worth showing. */
@Composable
private fun CardNotice(entry: LibraryEntry, unavailable: Boolean) {
    val notice = when {
        unavailable -> R.string.document_unavailable
        entry.isTransient -> R.string.document_from_another_app
        !entry.canWrite -> R.string.document_read_only
        else -> null
    } ?: return

    Text(
        text = stringResource(notice),
        style = MaterialTheme.typography.labelSmall,
        color = LocalSkin.current.colors.danger,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp),
    )
}

/** The scratch document, which has no file behind it and so no library row either. */
@Composable
fun DraftCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().testTag(DashboardTags.DRAFT_CARD),
        shape = RoundedCornerShape(skin.shape.medium.dp),
        colors = CardDefaults.cardColors(containerColor = skin.colors.accentSubtle),
        border = BorderStroke(1.dp, skin.colors.accent.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Article,
                contentDescription = null,
                tint = skin.colors.accent,
                modifier = Modifier.padding(end = 12.dp).size(20.dp),
            )
            Column {
                Text(
                    text = stringResource(R.string.unsaved_draft),
                    style = MaterialTheme.typography.titleSmall,
                    color = skin.colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.unsaved_draft_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = skin.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
