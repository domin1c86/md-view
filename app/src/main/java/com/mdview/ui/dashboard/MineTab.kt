package com.mdview.ui.dashboard

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import com.mdview.data.LanguageChoice
import com.mdview.data.ReadingSize
import com.mdview.data.RemoteImagePolicy
import com.mdview.data.Settings
import com.mdview.data.ThemeChoice

/** Test handles for the settings rows, whose labels repeat across groups. */
object SettingsTags {
    const val PANEL = "settings:panel"
    fun option(value: Enum<*>) = "settings:option:${value::class.simpleName}:${value.name}"
    const val DYNAMIC_COLOR = "settings:dynamicColor"
}

/**
 * Preferences.
 *
 * Everything here is applied the moment it is tapped rather than behind a save button —
 * with five settings and instant feedback on four of them, a confirm step would be
 * ceremony for its own sake.
 */
@Composable
fun MineTab(
    settings: Settings,
    onChange: ((Settings) -> Settings) -> Unit,
    onChangeLanguage: (LanguageChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
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

            // Dynamic colour is a no-op below Android 12, so the switch is not offered
            // there rather than shipping a control that visibly does nothing.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                HorizontalDivider()
                SwitchRow(
                    title = R.string.setting_dynamic_color,
                    body = R.string.setting_dynamic_color_body,
                    checked = settings.dynamicColor,
                    tag = SettingsTags.DYNAMIC_COLOR,
                    onCheckedChange = { on -> onChange { it.copy(dynamicColor = on) } },
                )
            }

            HorizontalDivider()
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

        SettingsGroup(R.string.settings_about) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.about_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(titleRes: Int, content: @Composable () -> Unit) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
    content()
}

/** A titled group of radio buttons — the shape three of the five settings share. */
@Composable
private fun <T : Enum<T>> OptionRow(
    title: Int,
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    onSelect: (T) -> Unit,
    body: Int? = null,
) {
    Column(Modifier.padding(top = 8.dp, bottom = 8.dp)) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        body?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    RadioButton(selected = option == selected, onClick = null)
                    Text(
                        text = stringResource(label(option)),
                        style = MaterialTheme.typography.bodyMedium,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .testTag(tag)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(checked = checked, onCheckedChange = null)
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
