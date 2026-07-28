package com.mdview.data

import com.mdview.ui.theme.BuiltInSkins
import com.mdview.ui.theme.Skin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The skins the user has imported, on top of the ones that ship with the app.
 *
 * Shaped like [SettingsStore] -- a plain [File] rather than a Context so it tests against
 * a temp directory, and an injectable [dispatcher] and [scope] so a test can drive the
 * writes. There is no index file: one skin is one `<id>.json`, and the directory listing
 * *is* the catalogue. That works here, unlike for [LibraryStore], because skins have no
 * ordering to preserve and no field that a second writer could clobber.
 *
 * **Part of the first read is synchronous, deliberately, and it is bounded.**
 * `MainActivity.applyWindowChrome` runs before `super.onCreate` and needs the active
 * skin's canvas colour, or the first frame flashes the wrong background -- so the
 * constructor loads the at most *two* files named by [bootLightId] and [bootDarkId], and
 * nothing at all when both are built-in, which is the default and the common case. The
 * rest of the catalogue is loaded by [loadAll] on [scope].
 */
class SkinStore(
    private val directory: File,
    bootLightId: String = BuiltInSkins.Paper.id,
    bootDarkId: String = BuiltInSkins.Ink.id,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {

    private val mutex = Mutex()
    private val _imported = MutableStateFlow(readBootSkins(bootLightId, bootDarkId))

    /** Imported skins only. Built-ins are constants and cost no I/O. */
    val imported: StateFlow<List<Skin>> = _imported.asStateFlow()

    init {
        scope.launch { loadAll() }
    }

    /** Built-ins first, then imported, each group in its own order. */
    val catalog: List<Skin> get() = BuiltInSkins.all + _imported.value

    /**
     * The skin [id] names, or the default for [dark] when it no longer exists.
     *
     * A missing id is normal rather than exceptional: the user can delete the skin that
     * a preference still points at, and a settings file can outlive the app that wrote it.
     */
    fun resolve(id: String, dark: Boolean): Skin =
        catalog.firstOrNull { it.id == id } ?: BuiltInSkins.default(dark)

    /** Reads every skin on disk into memory. Safe to call more than once. */
    suspend fun loadAll() {
        val loaded = withContext(dispatcher) { readAll() }
        mutex.withLock {
            // A concurrent import may have landed while this was reading; the in-memory
            // copy wins for those ids, since it is the one already on screen.
            val current = _imported.value.associateBy { it.id }
            _imported.value = (loaded.associateBy { it.id } + current).values.sortedBy { it.name }
        }
    }

    /**
     * Validates, stores and publishes the skin described by [text].
     *
     * Re-importing an id that is already present replaces it, which is what someone
     * iterating on their own skin file expects. Shadowing a built-in is refused instead:
     * silently overriding "midnight" would make a bug report impossible to read.
     */
    suspend fun import(text: String): Skin {
        val skin = SkinCodec.decode(text)
        if (BuiltInSkins.byId(skin.id) != null) {
            throw InvalidSkinException(InvalidSkinException.Reason.ReservedId)
        }

        return mutex.withLock {
            val existing = _imported.value
            if (existing.none { it.id == skin.id } && existing.size >= MAX_IMPORTED) {
                throw InvalidSkinException(InvalidSkinException.Reason.TooMany)
            }

            write(skin)
            _imported.value = (existing.filterNot { it.id == skin.id } + skin).sortedBy { it.name }
            skin
        }
    }

    /** Removes an imported skin. Built-ins refuse, and report so. */
    suspend fun delete(id: String): Boolean {
        if (BuiltInSkins.byId(id) != null) return false
        return mutex.withLock {
            withContext(dispatcher) { runCatching { fileFor(id).delete() } }
            val next = _imported.value.filterNot { it.id == id }
            val removed = next.size != _imported.value.size
            _imported.value = next
            removed
        }
    }

    private fun readBootSkins(lightId: String, darkId: String): List<Skin> =
        setOf(lightId, darkId)
            .filter { BuiltInSkins.byId(it) == null && SkinCodec.isValidId(it) }
            .mapNotNull(::readSkin)
            .sortedBy { it.name }

    private fun readAll(): List<Skin> = runCatching {
        directory.listFiles { file -> file.isFile && file.name.endsWith(FILE_SUFFIX) }
            .orEmpty()
            .sortedBy { it.name }
            .take(MAX_IMPORTED)
            .mapNotNull { file -> readSkin(file.name.removeSuffix(FILE_SUFFIX)) }
    }.getOrDefault(emptyList())

    /**
     * One skin off disk, or null.
     *
     * A file that no longer parses is skipped rather than thrown: a corrupt skin should
     * cost the user that skin, not every skin and not the launch.
     */
    private fun readSkin(id: String): Skin? = runCatching {
        val file = fileFor(id)
        if (!file.isFile || file.length() > SkinCodec.MAX_CHARS) return@runCatching null
        SkinCodec.decode(file.readText()).takeIf { it.id == id }
    }.getOrNull()

    private suspend fun write(skin: Skin) = withContext(dispatcher) {
        runCatching {
            directory.mkdirs()
            val encoded = SkinCodec.encode(skin)
            val scratch = File(directory, "${skin.id}$FILE_SUFFIX.tmp")
            scratch.writeText(encoded)
            if (!scratch.renameTo(fileFor(skin.id))) {
                fileFor(skin.id).writeText(encoded)
                scratch.delete()
            }
        }
    }

    // The id charset is enforced by SkinCodec before it ever reaches this, which is what
    // stops "../../databases/x" from becoming a path.
    private fun fileFor(id: String) = File(directory, "$id$FILE_SUFFIX")

    companion object {
        private const val FILE_SUFFIX = ".json"

        /** Enough for anyone hand-collecting themes, small enough to load in one go. */
        const val MAX_IMPORTED = 24
    }
}
