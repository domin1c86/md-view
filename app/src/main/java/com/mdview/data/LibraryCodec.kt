package com.mdview.data

/**
 * Serialises the document library to a flat text file.
 *
 * Pure Kotlin with no Android types, so it is unit-testable on the JVM the same way
 * `DocumentCodec` is. The format is one record per line, fields separated by tabs:
 *
 * ```
 * v2
 * content://…<TAB>notes.md<TAB>Release notes<TAB>An excerpt…<TAB>1753000000000<TAB>1<TAB>1<TAB>0<TAB>4f3c…
 * ```
 *
 * Excerpts come from arbitrary user documents, so every field is escaped — a tab or a
 * newline inside a heading would otherwise shift every later field by one and corrupt
 * the whole row.
 *
 * `v2` added the trailing [LibraryEntry.folderId]. `v1` is still **read**, as a row of
 * eight fields with nothing filed, because refusing it would silently empty the recents
 * list of everyone upgrading — the one outcome this codec's "drop the row, never throw"
 * rule exists to prevent. Everything is written as `v2` from then on.
 */
internal object LibraryCodec {

    private const val VERSION = "v2"
    private const val LEGACY_VERSION = "v1"
    private const val FIELD = '\t'
    private const val RECORD = '\n'
    private const val FIELD_COUNT = 9
    private const val LEGACY_FIELD_COUNT = 8

    fun encode(entries: List<LibraryEntry>): String = buildString {
        append(VERSION).append(RECORD)
        entries.forEach { entry ->
            append(escape(entry.uri)).append(FIELD)
            append(escape(entry.displayName)).append(FIELD)
            append(escape(entry.title.orEmpty())).append(FIELD)
            append(escape(entry.excerpt)).append(FIELD)
            append(entry.lastOpened).append(FIELD)
            append(entry.isFavorite.toFlag()).append(FIELD)
            append(entry.canWrite.toFlag()).append(FIELD)
            append(entry.isTransient.toFlag()).append(FIELD)
            append(escape(entry.folderId.orEmpty())).append(RECORD)
        }
    }

    /**
     * Reads [text] back. Anything unrecognised — a future version, a truncated row, a
     * timestamp that is not a number — is discarded rather than thrown, because a
     * corrupt recents list is a cosmetic problem and crashing on launch is not.
     */
    fun decode(text: String): List<LibraryEntry> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        val width = when (lines.firstOrNull()) {
            VERSION -> FIELD_COUNT
            LEGACY_VERSION -> LEGACY_FIELD_COUNT
            else -> return emptyList()
        }

        return lines.drop(1).mapNotNull { line ->
            val fields = line.split(FIELD)
            if (fields.size != width) return@mapNotNull null

            val uri = unescape(fields[0])
            if (uri.isBlank()) return@mapNotNull null

            LibraryEntry(
                uri = uri,
                displayName = unescape(fields[1]),
                title = unescape(fields[2]).takeIf { it.isNotBlank() },
                excerpt = unescape(fields[3]),
                lastOpened = fields[4].toLongOrNull() ?: return@mapNotNull null,
                isFavorite = fields[5].toFlagOrNull() ?: return@mapNotNull null,
                canWrite = fields[6].toFlagOrNull() ?: return@mapNotNull null,
                isTransient = fields[7].toFlagOrNull() ?: return@mapNotNull null,
                // Absent in v1, and an empty field in v2 means unfiled.
                folderId = fields.getOrNull(8)?.let(::unescape)?.takeIf { it.isNotBlank() },
            )
        }
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(char)
            }
        }
    }

    private fun unescape(value: String): String = buildString(value.length) {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char != '\\' || index == value.lastIndex) {
                append(char)
                index++
                continue
            }
            when (val escaped = value[index + 1]) {
                '\\' -> append('\\')
                't' -> append('\t')
                'n' -> append('\n')
                'r' -> append('\r')
                // Not a sequence we wrote; keep both characters rather than eat one.
                else -> append(char).append(escaped)
            }
            index += 2
        }
    }

    private fun Boolean.toFlag(): String = if (this) "1" else "0"

    private fun String.toFlagOrNull(): Boolean? = when (this) {
        "1" -> true
        "0" -> false
        else -> null
    }
}
