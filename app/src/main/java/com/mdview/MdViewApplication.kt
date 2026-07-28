package com.mdview

import android.app.Application
import android.content.Context
import com.mdview.data.LibraryStore
import com.mdview.data.SettingsStore
import com.mdview.data.SkinStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Owns the three stores that outlive any one screen.
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

    lateinit var skins: SkinStore
        private set

    lateinit var library: LibraryStore
        private set

    override fun onCreate() {
        super.onCreate()
        buildStores()
    }

    /**
     * Order matters: [SkinStore] needs the two active skin ids to know which files it
     * has to read synchronously, and those live in [SettingsStore].
     */
    private fun buildStores() {
        settings = SettingsStore(filesDir, scope = scope)
        skins = SkinStore(
            directory = File(filesDir, "skins"),
            bootLightId = settings.current.lightSkinId,
            bootDarkId = settings.current.darkSkinId,
            scope = scope,
        )
        library = LibraryStore(File(filesDir, "library"))
        scope.launch { library.load() }
    }

    /**
     * Rebuilds every store from whatever is now on disk.
     *
     * Instrumentation runs every test in one process, so deleting the files between
     * tests is not enough — the in-memory flows would keep serving the previous test's
     * library, skins and theme. Called from the rule that wipes app storage.
     */
    internal fun resetForTests() = buildStores()

    companion object {
        fun from(context: Context): MdViewApplication =
            context.applicationContext as MdViewApplication
    }
}
