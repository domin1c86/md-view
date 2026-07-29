package com.mdview.markdown

/**
 * What a Markdown image destination turns out to be, decided from the string alone.
 *
 * Pure Kotlin with no Android types, so the whole classification is unit-testable on the
 * JVM the way `DocumentCodec` and `SkinCodec` are.
 */
internal sealed interface ImageTarget {

    /** `http`/`https`. The remote-image privacy gate owns this one. */
    data object Remote : ImageTarget

    /** `data:`, `content:`, `file:` — already usable, so Coil takes the string verbatim. */
    data object Direct : ImageTarget

    /** A path to be resolved inside the document's own folder tree. */
    data class Local(val base: Base, val path: LocalPath) : ImageTarget

    /** Empty, escaping, ambiguous or oversized: nothing may be built from it. */
    data object Unusable : ImageTarget

    /** What a [Local] path is measured from. */
    enum class Base {
        /** The folder the document itself sits in — `./x.png`, `../assets/x.png`. */
        DocumentFolder,

        /** The root of the folder the user granted — `/images/x.png`. */
        TreeRoot,
    }
}

/**
 * A normalised relative path: climb [ascend] folders, then descend through [segments].
 *
 * `..` is reported rather than applied because how far it may climb depends on where the
 * document sits inside the granted tree, which this layer cannot know.
 */
internal data class LocalPath(val ascend: Int, val segments: List<String>)

/**
 * Turns a Markdown image destination into something a folder grant can be measured against.
 *
 * **The order of the steps below is a security control, not a matter of taste.** Three of
 * them are load-bearing, and each is easy to get wrong in a way that only shows up as a
 * traversal:
 *
 * - Backslashes are folded to `/` *before* anything inspects the path. Left literal,
 *   `..\..\secret` is a single segment that sails straight past the `..` check.
 * - Segments are split *before* they are percent-decoded. Decoding first would let `%2F`
 *   inject a separator that nothing had validated.
 * - `..` that walks above the base is refused, never clamped. Clamping silently changes
 *   which file is meant and might well succeed; the same reasoning made `SkinCodec` reject
 *   bad ids rather than sanitise them.
 */
internal object ImagePath {

    /** Deep enough for any real document, shallow enough that no id can be grown huge. */
    const val MAX_SEGMENTS = 32
    const val MAX_ASCEND = 32
    const val MAX_SEGMENT_CHARS = 255

    fun classify(destination: String?): ImageTarget {
        val raw = destination?.trim().orEmpty()
        if (raw.isEmpty()) return ImageTarget.Unusable

        // First, so nothing below can reinterpret a remote reference as a local file.
        if (isRemoteImage(raw)) return ImageTarget.Remote

        // A protocol-relative URL. `isRemoteImage` misses it -- it has no scheme at all --
        // and collapsing the empty segments would turn `//cdn/a.png` into a lookup for the
        // file `cdn/a.png`, quietly making a network reference local.
        if (raw.startsWith("//")) return ImageTarget.Unusable

        when (schemeLengthOf(raw)) {
            0 -> Unit
            // A one-letter "scheme" before a separator is a Windows drive, not a URI.
            // Letting it through would produce a path segment literally named `C:`.
            1 -> return ImageTarget.Unusable
            else -> return ImageTarget.Direct
        }

        // Fragment before query: `a.png?v=2#f` puts the query first, so cutting at `#`
        // first handles both orders. A filename holding a literal `?` or `#` has to be
        // percent-encoded, which every static-site generator already does.
        val trimmed = raw.substringBefore('#').substringBefore('?')
        if (trimmed.isEmpty()) return ImageTarget.Unusable

        val normalised = trimmed.replace('\\', '/')
        val base = if (normalised.startsWith('/')) {
            ImageTarget.Base.TreeRoot
        } else {
            ImageTarget.Base.DocumentFolder
        }

        var ascend = 0
        val segments = mutableListOf<String>()

        normalised.removePrefix("/").split('/').forEach { piece ->
            val segment = decode(piece) ?: return ImageTarget.Unusable
            when (segment) {
                // An empty piece is what an in-path `//` collapses to.
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex) else ascend++
                else -> {
                    if (segment.length > MAX_SEGMENT_CHARS) return ImageTarget.Unusable
                    segments += segment
                }
            }
            if (segments.size > MAX_SEGMENTS || ascend > MAX_ASCEND) return ImageTarget.Unusable
        }

        // There is nothing above a tree root by definition, and a folder is not an image.
        if (base == ImageTarget.Base.TreeRoot && ascend > 0) return ImageTarget.Unusable
        if (segments.isEmpty()) return ImageTarget.Unusable

        return ImageTarget.Local(base, LocalPath(ascend, segments))
    }

    /**
     * The length of the RFC 3986 scheme [value] starts with, or 0 when it has none.
     *
     * Only a scheme appearing before the first separator counts, so `img/a:b.png` stays a
     * path rather than becoming a URI with the scheme `img/a`.
     */
    private fun schemeLengthOf(value: String): Int {
        val colon = value.indexOf(':')
        if (colon <= 0) return 0

        val separator = value.indexOfFirst { it == '/' || it == '\\' }
        if (separator in 0..<colon) return 0

        if (!value[0].isAsciiLetter()) return 0
        for (index in 1..<colon) {
            val char = value[index]
            val allowed = char.isAsciiLetter() || char in '0'..'9' ||
                char == '+' || char == '-' || char == '.'
            if (!allowed) return 0
        }
        return colon
    }

    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

    /**
     * Percent-decodes one path segment, or null when the result would not be one.
     *
     * Hand-rolled rather than `java.net.URLDecoder`, which turns `+` into a space — right
     * for a form body, wrong for a filename. Bytes are accumulated before being decoded so
     * a multi-byte character survives: `%E4%B8%AD` is one `中`, not three replacements.
     */
    private fun decode(segment: String): String? {
        // Nothing was encoded, so nothing can be hiding: `/` cannot appear (the split
        // consumed it) and nor can `\` (it was folded before the split).
        if ('%' !in segment) return segment

        val bytes = ByteArray(segment.length * 4)
        var length = 0
        var index = 0

        fun push(byte: Int) {
            bytes[length++] = byte.toByte()
        }

        while (index < segment.length) {
            val char = segment[index]
            if (char == '%' && index + 2 <= segment.lastIndex) {
                val high = Character.digit(segment[index + 1], 16)
                val low = Character.digit(segment[index + 2], 16)
                if (high >= 0 && low >= 0) {
                    push(high shl 4 or low)
                    index += 3
                    continue
                }
            }
            // Not a valid escape; keep the character as written, the way browsers do.
            char.toString().toByteArray(Charsets.UTF_8).forEach { push(it.toInt()) }
            index++
        }

        val decoded = String(bytes, 0, length, Charsets.UTF_8)

        // The escape decoded back into something never validated as a single segment:
        // `%2F` a separator, `%00` a terminator, `%2e%2e` a climb the caller would have
        // seen and budgeted for had it been written plainly.
        val disguised = decoded.any { it == '/' || it == '\\' || it == '\u0000' } ||
            decoded == "." || decoded == ".."
        return decoded.takeUnless { disguised }
    }
}
