package com.mdview.data

/**
 * Serialises the granted image folders to a flat text file.
 *
 * The same escaped-TSV shape as [LibraryCodec], and for the same reason: a display name
 * comes from a `DocumentsProvider` and may hold a tab, which would otherwise shift every
 * later field along and corrupt the row.
 *
 * ```
 * v1
 * content://…/tree/primary%3ADocuments<TAB>Documents<TAB>1753000000000
 * ```
 */
internal object FolderGrantCodec {

    private const val VERSION = "v1"
    private const val FIELD = '\t'
    private const val RECORD = '\n'
    private const val FIELD_COUNT = 3

    fun encode(grants: List<FolderGrant>): String = buildString {
        append(VERSION).append(RECORD)
        grants.forEach { grant ->
            append(escape(grant.treeUri)).append(FIELD)
            append(escape(grant.displayName)).append(FIELD)
            append(grant.grantedAt).append(RECORD)
        }
    }

    /**
     * Reads [text] back, discarding anything unrecognised.
     *
     * A row that cannot be parsed is dropped rather than thrown, matching [LibraryCodec]:
     * losing one folder means the user is asked for it again, and crashing on launch is a
     * far worse answer to a corrupt file.
     */
    fun decode(text: String): List<FolderGrant> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.firstOrNull() != VERSION) return emptyList()

        return lines.drop(1).mapNotNull { line ->
            val fields = line.split(FIELD)
            if (fields.size != FIELD_COUNT) return@mapNotNull null

            val treeUri = unescape(fields[0])
            if (treeUri.isBlank()) return@mapNotNull null

            FolderGrant(
                treeUri = treeUri,
                displayName = unescape(fields[1]),
                grantedAt = fields[2].toLongOrNull() ?: return@mapNotNull null,
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
