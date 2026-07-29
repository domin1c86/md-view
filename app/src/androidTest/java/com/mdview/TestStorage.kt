package com.mdview

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Returns app-private storage to what a fresh install looks like.
 *
 * Drafts, the library, the settings, the imported skins and the granted image folders all
 * outlive a process by design, which also means they outlive a test. This has to run
 * **before the Activity
 * starts** -- by the time an `@Before` method runs, the ViewModel has already restored a
 * draft -- so every suite chains it outside the Compose rule rather than calling it from
 * a setup method.
 *
 * Deleting the files is not enough on its own: the stores are process-wide singletons
 * holding their contents in memory, and instrumentation runs every test in one process,
 * so [MdViewApplication.resetForTests] has to rebuild them too.
 *
 * It lives here, shared, rather than being copied into each suite, because the copies had
 * already drifted -- and a new store is otherwise very easy to forget in one of four
 * places at once.
 */
internal object TestStorage {

    val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * [seed] runs after the wipe and before the stores are rebuilt, which is the only
     * window where a test can put files on disk and have them read as if they had been
     * there all along.
     */
    fun wipe(seed: (Context) -> Unit = {}) {
        val context = context
        File(context.filesDir, "drafts").deleteRecursively()
        File(context.filesDir, "library").deleteRecursively()
        File(context.filesDir, "skins").deleteRecursively()
        File(context.filesDir, "folders").deleteRecursively()
        File(context.filesDir, "settings.txt").delete()

        seed(context)

        MdViewApplication.from(context).resetForTests()
    }
}
