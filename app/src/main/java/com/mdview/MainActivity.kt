package com.mdview

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdview.data.SettingsStore
import com.mdview.data.ThemeChoice
import com.mdview.ui.MdViewRoot
import com.mdview.ui.theme.MdViewTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.factory(this) }

    private val settingsStore: SettingsStore get() = MdViewApplication.from(this).settings

    override fun onCreate(savedInstanceState: Bundle?) {
        // Read before super.onCreate: enableEdgeToEdge decides the status-bar icon
        // colours here, and getting them from the system's night mode rather than the
        // user's choice paints dark icons onto a dark bar.
        applyWindowChrome(settingsStore.current.theme.isDark(systemIsDark()))
        super.onCreate(savedInstanceState)
        handleViewIntent(intent)

        setContent {
            val settings by settingsStore.state.collectAsStateWithLifecycle()
            val dark = settings.theme.isDark(isSystemInDarkTheme())

            LaunchedEffect(dark) { applyWindowChrome(dark) }

            MdViewTheme(darkTheme = dark, dynamicColor = settings.dynamicColor) {
                Surface(Modifier.fillMaxSize()) {
                    MdViewRoot(
                        viewModel = viewModel,
                        settings = settings,
                        onChangeSettings = settingsStore::update,
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
        window.setBackgroundDrawable(
            (if (dark) WINDOW_DARK else WINDOW_LIGHT).toInt().toDrawable()
        )
    }

    private fun systemIsDark(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private companion object {
        // Matches LightBackground / DarkBackground; this only shows before Compose draws.
        const val WINDOW_LIGHT = 0xFFFDFBFF
        const val WINDOW_DARK = 0xFF1B1B1F
    }
}

/** Whether this choice means a dark scheme, given what the system is currently doing. */
private fun ThemeChoice.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeChoice.System -> systemDark
    ThemeChoice.Light -> false
    ThemeChoice.Dark -> true
}
