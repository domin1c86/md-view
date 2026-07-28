package com.mdview.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mdview.R
import com.mdview.ui.theme.BuiltInSkins
import com.mdview.ui.theme.LocalSkin
import com.mdview.ui.theme.Skin

/**
 * The light or dark half of the skin picker.
 *
 * A [FlowRow] rather than a horizontally scrolling row, deliberately: `SettingsTest`
 * reaches these with `performScrollTo()`, and a nested scroll container inside the
 * panel's vertical one is exactly the sort of thing that makes that stop working.
 *
 * [enabled] goes false while Material You is on. The swatches stay visible but dimmed
 * rather than disappearing -- hiding controls that the switch directly above them just
 * disabled reads as a bug rather than as an explanation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkinGallery(
    skins: List<Skin>,
    selectedId: String,
    dark: Boolean,
    enabled: Boolean,
    onSelect: (Skin) -> Unit,
    onDelete: (Skin) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        skins.forEach { skin ->
            SkinSwatch(
                skin = skin,
                selected = skin.id == selectedId,
                dark = dark,
                enabled = enabled,
                onSelect = { onSelect(skin) },
                onDelete = { onDelete(skin) },
            )
        }
    }
}

/**
 * One skin, previewed in its own colours.
 *
 * The preview is painted from the skin's tokens rather than from an image, so an
 * imported skin gets a real preview the moment it lands with no extra work by its author.
 */
@Composable
private fun SkinSwatch(
    skin: Skin,
    selected: Boolean,
    dark: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val active = LocalSkin.current
    val outline = if (selected) active.colors.accent else active.colors.border

    Column(
        modifier = Modifier
            .width(SWATCH_WIDTH)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .testTag(SettingsTags.skin(dark, skin.id)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            SkinPreview(skin, outline, selected)

            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp)
                        .background(active.colors.accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = active.colors.onAccent,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }

            // Built-ins have no remove affordance at all, rather than a disabled one.
            if (skin.isRemovable) {
                IconButton(
                    onClick = onDelete,
                    enabled = enabled,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(26.dp)
                        .testTag(SettingsTags.deleteSkin(skin.id)),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.remove_skin),
                        tint = active.colors.textMuted,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        Text(
            text = skin.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) active.colors.accent else active.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** A miniature of the app drawn in [skin]'s own colours: canvas, a card, some text. */
@Composable
private fun SkinPreview(skin: Skin, outline: Color, selected: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .size(SWATCH_WIDTH, SWATCH_HEIGHT)
            .clip(shape)
            .background(skin.colors.canvas)
            .border(if (selected) 2.dp else 1.dp, outline, shape)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(skin.colors.accent, CircleShape))
            Box(
                Modifier
                    .padding(start = 5.dp)
                    .height(5.dp)
                    .width(26.dp)
                    .background(skin.colors.textPrimary, RoundedCornerShape(3.dp))
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(skin.colors.surface)
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                Modifier
                    .height(4.dp)
                    .fillMaxWidth(0.75f)
                    .background(skin.colors.textPrimary, RoundedCornerShape(2.dp))
            )
            Box(
                Modifier
                    .height(4.dp)
                    .fillMaxWidth()
                    .background(skin.colors.textMuted, RoundedCornerShape(2.dp))
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(skin.colors.codeBackground)
                .border(1.dp, skin.colors.border, RoundedCornerShape(4.dp))
        )
    }
}

/** Only imported skins can be removed; the built-in ones are constants in the binary. */
private val Skin.isRemovable: Boolean
    get() = BuiltInSkins.byId(id) == null

private val SWATCH_WIDTH = 96.dp
private val SWATCH_HEIGHT = 74.dp
private const val DISABLED_ALPHA = 0.4f
