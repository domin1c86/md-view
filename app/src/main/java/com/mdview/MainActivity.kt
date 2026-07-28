package com.mdview

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mdview.ui.MdViewRoot
import com.mdview.ui.theme.MdViewTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.factory(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleViewIntent(intent)

        setContent {
            MdViewTheme {
                Surface(Modifier.fillMaxSize()) {
                    MdViewRoot(viewModel)
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
}
