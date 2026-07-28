package com.mdview.data

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
 * User preferences, stored as `key=value` lines in app-private storage.
 *
 * Every value is an enum name or a boolean, so unlike [LibraryCodec] there is nothing
 * here that needs escaping. Takes a [File] rather than a Context, per [DraftStore], so
 * it stays unit-testable on the JVM.
 *
 * **The initial read is synchronous, on purpose.** Two callers need a value before any
 * coroutine could have delivered one: the theme, which would otherwise flash the wrong
 * colours on the first frame, and the locale wrapper, which runs in
 * `Activity.attachBaseContext` — before `onCreate`. The file is a few hundred bytes and
 * this happens once per process, in `Application.onCreate`.
 */
class SettingsStore(
    private val directory: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {

    private val mutex = Mutex()
    private val _state = MutableStateFlow(readBlocking())
    val state: StateFlow<Settings> = _state.asStateFlow()

    /** The current settings, readable off the main thread and outside composition. */
    val current: Settings get() = _state.value

    /** Applies [transform] and persists the result. Returns once memory is updated. */
    fun update(transform: (Settings) -> Settings) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        scope.launch { write(next) }
    }

    /** Persists whatever is in memory. Used by tests that need the write to have landed. */
    suspend fun flush() = write(_state.value)

    private suspend fun write(settings: Settings) {
        mutex.withLock {
            withContext(dispatcher) {
                runCatching {
                    directory.mkdirs()
                    val encoded = encode(settings)
                    val scratch = File(directory, "$FILE_NAME.tmp")
                    scratch.writeText(encoded)
                    if (!scratch.renameTo(file)) {
                        file.writeText(encoded)
                        scratch.delete()
                    }
                }
            }
        }
    }

    private fun readBlocking(): Settings =
        runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull()?.let(::decode)
            ?: Settings()

    private val file: File get() = File(directory, FILE_NAME)

    internal companion object {
        private const val FILE_NAME = "settings.txt"

        private const val KEY_THEME = "theme"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_DYNAMIC_COLOR = "dynamicColor"
        private const val KEY_REMOTE_IMAGES = "remoteImages"
        private const val KEY_READING_SIZE = "readingSize"

        // Appended after the original five on purpose. SettingsStoreTest patches the
        // encoded text by replacing the literal "readingSize=Medium", which silently
        // becomes a no-op -- a test that passes for the wrong reason -- if that line
        // ever moves or is renamed.
        private const val KEY_LIGHT_SKIN = "lightSkin"
        private const val KEY_DARK_SKIN = "darkSkin"

        fun encode(settings: Settings): String = buildString {
            appendLine("$KEY_THEME=${settings.theme.name}")
            appendLine("$KEY_LANGUAGE=${settings.language.name}")
            appendLine("$KEY_DYNAMIC_COLOR=${settings.dynamicColor}")
            appendLine("$KEY_REMOTE_IMAGES=${settings.remoteImages.name}")
            appendLine("$KEY_READING_SIZE=${settings.readingSize.name}")
            appendLine("$KEY_LIGHT_SKIN=${settings.lightSkinId}")
            appendLine("$KEY_DARK_SKIN=${settings.darkSkinId}")
        }

        /**
         * Unknown keys and unparseable values fall back to the default for that one
         * field. A settings file written by a newer build, or hand-edited into nonsense,
         * should cost the user one preference rather than every preference.
         */
        fun decode(text: String): Settings {
            val values = text.lineSequence()
                .mapNotNull { line ->
                    val separator = line.indexOf('=').takeIf { it > 0 } ?: return@mapNotNull null
                    line.take(separator).trim() to line.substring(separator + 1).trim()
                }
                .toMap()

            val defaults = Settings()
            return Settings(
                theme = values[KEY_THEME].toEnum(defaults.theme),
                language = values[KEY_LANGUAGE].toEnum(defaults.language),
                dynamicColor = values[KEY_DYNAMIC_COLOR]?.toBooleanStrictOrNull()
                    ?: defaults.dynamicColor,
                remoteImages = values[KEY_REMOTE_IMAGES].toEnum(defaults.remoteImages),
                readingSize = values[KEY_READING_SIZE].toEnum(defaults.readingSize),
                // Validated on the way in, not just on the way out: a skin id becomes a
                // filename, so a hand-edited "../../databases/x" must never survive here.
                lightSkinId = values[KEY_LIGHT_SKIN].toSkinId(defaults.lightSkinId),
                darkSkinId = values[KEY_DARK_SKIN].toSkinId(defaults.darkSkinId),
            )
        }

        private fun String?.toSkinId(fallback: String): String =
            this?.takeIf(SkinCodec::isValidId) ?: fallback

        private inline fun <reified T : Enum<T>> String?.toEnum(fallback: T): T =
            enumValues<T>().firstOrNull { it.name == this } ?: fallback
    }
}
