package com.mdview

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdview.data.AppLocale
import com.mdview.data.LanguageChoice
import com.mdview.data.SettingsStore
import com.mdview.data.SkinStore
import com.mdview.data.isDark
import com.mdview.ui.MdViewRoot
import com.mdview.ui.theme.MdViewTheme
import androidx.compose.ui.graphics.Color as ComposeColor

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.factory(this) }

    private val settingsStore: SettingsStore get() = MdViewApplication.from(this).settings

    private val skinStore: SkinStore get() = MdViewApplication.from(this).skins

    /**
     * Below API 33 this is the only place the in-app language can be applied -- it runs
     * before onCreate, and everything resolved from this context afterwards inherits it.
     * `this.filesDir` is not available yet, hence reading the store through [newBase].
     */
    override fun attachBaseContext(newBase: Context) {
        val choice = MdViewApplication.from(newBase).settings.current.language
        super.attachBaseContext(AppLocale.wrap(newBase, choice))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Read before super.onCreate: enableEdgeToEdge decides the status-bar icon
        // colours here, and getting them from the system's night mode rather than the
        // user's choice paints dark icons onto a dark bar.
        applyWindowChrome(settingsStore.current.theme.isDark(systemIsDark()))
        super.onCreate(savedInstanceState)
        handleViewIntent(intent)

        setContent {
            val settings by settingsStore.state.collectAsStateWithLifecycle()
            val imported by skinStore.imported.collectAsStateWithLifecycle()
            val dark = settings.theme.isDark(isSystemInDarkTheme())

            // `imported` is a dependency rather than noise: importing or deleting a skin
            // has to re-resolve the active one, or the panel shows a new skin selected
            // while the app carries on drawing the old one.
            val skin = remember(settings.lightSkinId, settings.darkSkinId, dark, imported) {
                skinStore.resolve(if (dark) settings.darkSkinId else settings.lightSkinId, dark)
            }

            LaunchedEffect(dark, skin.id, settings.dynamicColor) { applyWindowChrome(dark) }

            MdViewTheme(skin = skin, dynamicColor = settings.dynamicColor) {
                Surface(Modifier.fillMaxSize()) {
                    MdViewRoot(
                        viewModel = viewModel,
                        settings = settings.copy(
                            language = AppLocale.current(this, settings.language),
                        ),
                        systemDark = isSystemInDarkTheme(),
                        onChangeSettings = settingsStore::update,
                        onChangeLanguage = ::changeLanguage,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleViewIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        // Backgrounding is the last moment guaranteed before the process may be killed,
        // so the autosave debounce is cut short here rather than waited out.
        viewModel.flushDraft()
    }

    /** Opens a document handed over by a file manager or another app. */
    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        // openFromIntent skips the re-read for a URI that is already loaded, so a
        // rotation -- which re-delivers the same intent to a fresh Activity -- does not
        // throw away the user's edits. It still navigates either way, or tapping the
        // same file again after backing out to the dashboard would appear to do nothing.
        intent.data?.let(viewModel::openFromIntent)
    }

    /**
     * Keeps the parts of the window Compose cannot reach in step with the theme.
     *
     * The window background comes from `Theme.MdView`, which the platform resolves
     * against the *system's* night mode. Forcing Dark on a light device would otherwise
     * flash white on every cold start and behind the keyboard.
     */
    private fun applyWindowChrome(dark: Boolean) {
        val bars = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        window.setBackgroundDrawable(windowBackground(dark).toArgb().toDrawable())
    }

    /**
     * The colour behind the first frame, taken from whatever will actually be drawn.
     *
     * This used to be two hardcoded constants hand-mirrored from the palette, which drifted
     * the moment the palette moved and were simply wrong under Material You. Both branches
     * here are ordinary function calls -- `dynamicLightColorScheme` is not `@Composable` --
     * so this still runs before `super.onCreate`, which is where it has to run for
     * `enableEdgeToEdge` to pick the right status-bar icons.
     */
    private fun windowBackground(dark: Boolean): ComposeColor {
        val settings = settingsStore.current
        if (settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scheme =
                if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            return scheme.background
        }
        val id = if (dark) settings.darkSkinId else settings.lightSkinId
        return skinStore.resolve(id, dark).colors.canvas
    }

    /**
     * Switching language has to restart the Activity so every string is re-resolved.
     * On API 33+ the platform does that itself once [AppLocale.apply] lands; below it,
     * the wrapped context is only built in [attachBaseContext], so recreate explicitly.
     */
    private fun changeLanguage(choice: LanguageChoice) {
        if (settingsStore.current.language == choice) return
        settingsStore.update { it.copy(language = choice) }
        AppLocale.apply(this, choice)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) recreate()
    }

    private fun systemIsDark(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
}
