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
import com.mdview.ui.MdViewApp
import com.mdview.ui.theme.MdViewTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleViewIntent(intent)

        setContent {
            MdViewTheme {
                Surface(Modifier.fillMaxSize()) {
                    MdViewApp(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleViewIntent(intent)
    }

    /** Opens a document handed over by a file manager or another app. */
    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        // openFromIntent ignores a URI that is already loaded, so a rotation --
        // which re-delivers the same intent to a fresh Activity -- does not throw
        // away the user's edits.
        intent.data?.let(viewModel::openFromIntent)
    }
}
