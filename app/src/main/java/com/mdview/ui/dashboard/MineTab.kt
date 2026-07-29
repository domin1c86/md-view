package com.mdview.ui.dashboard

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mdview.BuildConfig
import com.mdview.R
import com.mdview.data.FolderGrant
import com.mdview.data.FolderGrantStore
import com.mdview.data.LanguageChoice
import com.mdview.data.ReadingSize
import com.mdview.data.RemoteImagePolicy
import com.mdview.data.Settings
import com.mdview.data.ThemeChoice
import com.mdview.data.isDark
import com.mdview.ui.theme.LocalSkin
import com.mdview.ui.theme.Skin

/** Test handles for the settings rows, whose labels repeat across groups. */
object SettingsTags {
    const val PANEL = "settings:panel"
    fun option(value: Enum<*>) = "settings:option:${value::class.simpleName}:${value.name}"
    const val DYNAMIC_COLOR = "settings:dynamicColor"

    fun skin(dark: Boolean, id: String) = "settings:skin:${if (dark) "dark" else "light"}:$id"
    fun deleteSkin(id: String) = "settings:deleteSkin:$id"
    const val IMPORT_SKIN = "settings:importSkin"
    const val IMPORT_ERROR = "settings:importError"

    const val FOLDERS = "settings:folders"
    const val FOLDERS_EMPTY = "settings:foldersEmpty"
    fun folder(treeUri: String) = "settings:folder:$treeUri"
    fun forgetFolder(treeUri: String) = "settings:forgetFolder:$treeUri"
}

/**
 * Preferences.
 *
 * Everything here is applied the moment it is tapped rather than behind a save button —
 * with instant feedback on almost all of it, a confirm step would be ceremony for its
 * own sake.
 *
 * **The root must stay a single vertically scrolling [Column].** `SettingsTest` reaches
 * rows with `performScrollTo()`, which needs one scroll container and no lazy list.
 */
@Composable
fun MineTab(
    settings: Settings,
    skins: List<Skin>,
    systemDark: Boolean,
    onChange: ((Settings) -> Settings) -> Unit,
    onChangeLanguage: (LanguageChoice) -> Unit,
    onImportSkin: () -> Unit,
    onDeleteSkin: (Skin) -> Unit,
    importError: Int?,
    folders: List<FolderGrant>,
    onForgetFolder: (FolderGrant) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Material You is only offered from Android 12; below it the switch would be a
    // control that visibly does nothing, so the skins are always live there.
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicOn = dynamicAvailable && settings.dynamicColor
    val dark = settings.theme.isDark(systemDark)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag(SettingsTags.PANEL),
    ) {
        SettingsGroup(R.string.settings_appearance) {
            OptionRow(
                title = R.string.setting_theme,
                options = ThemeChoice.entries,
                selected = settings.theme,
                label = ::themeLabel,
                onSelect = { choice -> onChange { it.copy(theme = choice) } },
            )

            if (dynamicAvailable) {
                HorizontalDivider(color = LocalSkin.current.colors.divider)
                SwitchRow(
                    title = R.string.setting_dynamic_color,
                    body = R.string.setting_dynamic_color_body,
                    checked = settings.dynamicColor,
                    tag = SettingsTags.DYNAMIC_COLOR,
                    onCheckedChange = { on -> onChange { it.copy(dynamicColor = on) } },
                )
            }

            SettingsLabel(
                title = R.string.setting_skin_light,
                // The caption explains the dimming rather than leaving the user to guess
                // why a row full of swatches stopped responding.
                body = if (dynamicOn) R.string.setting_skin_dynamic_note else R.string.setting_skin_body,
                // The live half of the picker is emphasised so it is obvious which of the
                // two the app is currently drawing.
                emphasised = !dark,
            )
            SkinGallery(
                skins = skins.filterNot { it.dark },
                selectedId = settings.lightSkinId,
                dark = false,
                enabled = !dynamicOn,
                onSelect = { skin -> onChange { it.copy(lightSkinId = skin.id) } },
                onDelete = onDeleteSkin,
            )

            SettingsLabel(title = R.string.setting_skin_dark, emphasised = dark)
            SkinGallery(
                skins = skins.filter { it.dark },
                selectedId = settings.darkSkinId,
                dark = true,
                enabled = !dynamicOn,
                onSelect = { skin -> onChange { it.copy(darkSkinId = skin.id) } },
                onDelete = onDeleteSkin,
            )

            ImportSkinRow(onImportSkin, importError)

            HorizontalDivider(color = LocalSkin.current.colors.divider)
            OptionRow(
                title = R.string.setting_language,
                options = LanguageChoice.entries,
                selected = settings.language,
                label = ::languageLabel,
                // Not routed through onChange: switching language also has to tell the
                // platform and restart the Activity, which only the Activity can do.
                onSelect = onChangeLanguage,
            )
        }

        SettingsGroup(R.string.settings_reading) {
            OptionRow(
                title = R.string.setting_reading_size,
                body = R.string.setting_reading_size_body,
                options = ReadingSize.entries,
                selected = settings.readingSize,
                label = ::readingSizeLabel,
                onSelect = { choice -> onChange { it.copy(readingSize = choice) } },
            )
        }

        SettingsGroup(R.string.settings_privacy) {
            OptionRow(
                title = R.string.setting_remote_images,
                body = R.string.setting_remote_images_body,
                options = RemoteImagePolicy.entries,
                selected = settings.remoteImages,
                label = ::remoteImagesLabel,
                onSelect = { choice -> onChange { it.copy(remoteImages = choice) } },
            )
        }

        SettingsGroup(R.string.settings_folders) {
            FolderGrantList(folders = folders, onForget = onForgetFolder)
        }

        SettingsGroup(R.string.settings_about) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyLarge,
                    color = LocalSkin.current.colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.about_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalSkin.current.colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * The "Import a skin…" affordance and whatever went wrong last time.
 *
 * The failure is reported inline rather than through a snackbar because the dashboard's
 * `Scaffold` has no `SnackbarHost`, and adding one for a single message would be more
 * plumbing than the message is worth.
 */
@Composable
private fun ImportSkinRow(onImportSkin: () -> Unit, importError: Int?) {
    val skin = LocalSkin.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onImportSkin)
            .testTag(SettingsTags.IMPORT_SKIN)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.FileDownload,
            contentDescription = null,
            tint = skin.colors.accent,
            modifier = Modifier.padding(end = 14.dp).size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.import_skin),
                style = MaterialTheme.typography.bodyLarge,
                color = skin.colors.accent,
            )
            Text(
                text = stringResource(R.string.import_skin_body),
                style = MaterialTheme.typography.bodySmall,
                color = skin.colors.textMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }

    importError?.let { message ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(
                    skin.colors.danger.copy(alpha = 0.10f),
                    RoundedCornerShape(skin.shape.small.dp),
                )
                .border(
                    1.dp,
                    skin.colors.danger.copy(alpha = 0.35f),
                    RoundedCornerShape(skin.shape.small.dp),
                )
                .testTag(SettingsTags.IMPORT_ERROR)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = skin.colors.danger,
                modifier = Modifier.padding(end = 10.dp).size(18.dp),
            )
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodySmall,
                color = skin.colors.danger,
            )
        }
    }
}

/**
 * The folders MdView may read images from, each with a way to hand it back.
 *
 * Plain rows in the panel's own scroller rather than a `LazyColumn`: the Mine tab must
 * stay a single scroll container for `performScrollTo()`, and [FolderGrantStore.MAX_GRANTS]
 * is what makes drawing them all cheap enough for that to be free.
 */
@Composable
private fun FolderGrantList(folders: List<FolderGrant>, onForget: (FolderGrant) -> Unit) {
    val skin = LocalSkin.current

    Column(Modifier.testTag(SettingsTags.FOLDERS)) {
        Text(
            text = stringResource(R.string.setting_folders_body),
            style = MaterialTheme.typography.bodySmall,
            color = skin.colors.textMuted,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )

        if (folders.isEmpty()) {
            // A caption rather than nothing, so the group is never a bare heading.
            Text(
                text = stringResource(R.string.folders_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = skin.colors.textSecondary,
                modifier = Modifier
                    .testTag(SettingsTags.FOLDERS_EMPTY)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            return@Column
        }

        folders.forEach { grant ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SettingsTags.folder(grant.treeUri))
                    .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    tint = skin.colors.textSecondary,
                    modifier = Modifier.padding(end = 14.dp).size(22.dp),
                )
                Text(
                    text = grant.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = skin.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onForget(grant) },
                    modifier = Modifier.testTag(SettingsTags.forgetFolder(grant.treeUri)),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.folder_forget),
                        tint = skin.colors.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(titleRes: Int, content: @Composable () -> Unit) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.labelLarge,
        color = LocalSkin.current.colors.accent,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
    content()
}

/** The title-and-caption pair that sits above a control which is not an [OptionRow]. */
@Composable
private fun SettingsLabel(title: Int, body: Int? = null, emphasised: Boolean = false) {
    val skin = LocalSkin.current
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.bodyLarge,
            color = if (emphasised) skin.colors.textPrimary else skin.colors.textSecondary,
        )
        body?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = skin.colors.textMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** A titled group of radio buttons — the shape three of the settings share. */
@Composable
private fun <T : Enum<T>> OptionRow(
    title: Int,
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    onSelect: (T) -> Unit,
    body: Int? = null,
) {
    val skin = LocalSkin.current
    Column(Modifier.padding(top = 8.dp, bottom = 8.dp)) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.bodyLarge,
            color = skin.colors.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        body?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = skin.colors.textMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
        Column(Modifier.selectableGroup()) {
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = option == selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(option) },
                        )
                        .testTag(SettingsTags.option(option))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = option == selected,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = skin.colors.accent,
                            unselectedColor = skin.colors.textMuted,
                        ),
                    )
                    Text(
                        text = stringResource(label(option)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = skin.colors.textPrimary,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: Int,
    body: Int,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    val skin = LocalSkin.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .testTag(tag)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.bodyLarge,
                color = skin.colors.textPrimary,
            )
            Text(
                text = stringResource(body),
                style = MaterialTheme.typography.bodySmall,
                color = skin.colors.textMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = skin.colors.onAccent,
                checkedTrackColor = skin.colors.accent,
            ),
        )
    }
}

private fun themeLabel(choice: ThemeChoice): Int = when (choice) {
    ThemeChoice.System -> R.string.theme_system
    ThemeChoice.Light -> R.string.theme_light
    ThemeChoice.Dark -> R.string.theme_dark
}

private fun languageLabel(choice: LanguageChoice): Int = when (choice) {
    LanguageChoice.System -> R.string.language_system
    LanguageChoice.English -> R.string.language_english
    LanguageChoice.Chinese -> R.string.language_chinese
}

private fun readingSizeLabel(choice: ReadingSize): Int = when (choice) {
    ReadingSize.Small -> R.string.reading_size_small
    ReadingSize.Medium -> R.string.reading_size_medium
    ReadingSize.Large -> R.string.reading_size_large
}

private fun remoteImagesLabel(choice: RemoteImagePolicy): Int = when (choice) {
    RemoteImagePolicy.Never -> R.string.remote_images_never
    RemoteImagePolicy.Unmetered -> R.string.remote_images_unmetered
    RemoteImagePolicy.Always -> R.string.remote_images_always
}
