package com.mdview.data

/**
 * Serialises the user's document folders to a flat text file.
 *
 * The same escaped-TSV shape as [FolderGrantCodec] and [LibraryCodec], and for the same
 * reason: a folder name is typed by the user and may hold a tab, which would otherwise
 * shift every later field along and corrupt the row.
 *
 * ```
 * v1
 * 4f3c…<TAB>Work<TAB>1753000000000
 * ```
 */
internal object LibraryFolderCodec {

    private const val VERSION = "v1"
    private const val FIELD = '\t'
    private const val RECORD = '\n'
    private const val FIELD_COUNT = 3

    fun encode(folders: List<LibraryFolder>): String = buildString {
        append(VERSION).append(RECORD)
        folders.forEach { folder ->
            append(escape(folder.id)).append(FIELD)
            append(escape(folder.name)).append(FIELD)
            append(folder.createdAt).append(RECORD)
        }
    }

    /**
     * Reads [text] back, discarding anything unrecognised.
     *
     * A row that cannot be parsed is dropped rather than thrown, matching the other two
     * codecs: losing one folder costs the user a re-file, and crashing on launch is a far
     * worse answer to a corrupt file. [LibraryStore] then unfiles any document left
     * pointing at a folder that did not survive.
     */
    fun decode(text: String): List<LibraryFolder> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.firstOrNull() != VERSION) return emptyList()

        return lines.drop(1).mapNotNull { line ->
            val fields = line.split(FIELD)
            if (fields.size != FIELD_COUNT) return@mapNotNull null

            val id = unescape(fields[0])
            val name = unescape(fields[1])
            // A nameless folder would draw as an unlabelled chip nobody could identify,
            // and an id-less one could never be matched to the documents inside it.
            if (id.isBlank() || name.isBlank()) return@mapNotNull null

            LibraryFolder(
                id = id,
                name = name,
                createdAt = fields[2].toLongOrNull() ?: return@mapNotNull null,
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
}
