package com.mdview

import android.app.Application
import android.content.Context
import com.mdview.data.LibraryStore
import com.mdview.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Owns the two stores that outlive any one screen.
 *
 * They have to be process-wide singletons rather than per-ViewModel instances: the
 * theme is read in [MainActivity.attachBaseContext] while the Mine tab writes it from a
 * ViewModel, and the dashboard reads the library while the document screen writes it.
 * Two instances would mean two `StateFlow`s, and changing a setting would appear to do
 * nothing.
 *
 * [SettingsStore] must exist before the first Activity attaches, which is why it is
 * built here in `onCreate` rather than lazily.
 */
class MdViewApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var settings: SettingsStore
        private set

    lateinit var library: LibraryStore
        private set

    override fun onCreate() {
        super.onCreate()
        buildStores()
    }

    private fun buildStores() {
        settings = SettingsStore(filesDir, scope = scope)
        library = LibraryStore(File(filesDir, "library"))
        scope.launch { library.load() }
    }

    /**
     * Rebuilds both stores from whatever is now on disk.
     *
     * Instrumentation runs every test in one process, so deleting the files between
     * tests is not enough — the in-memory flows would keep serving the previous test's
     * library and theme. Called from the rule that wipes app storage.
     */
    internal fun resetForTests() = buildStores()

    companion object {
        fun from(context: Context): MdViewApplication =
            context.applicationContext as MdViewApplication
    }
}
