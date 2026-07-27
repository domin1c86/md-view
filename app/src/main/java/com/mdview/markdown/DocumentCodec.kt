package com.mdview.markdown

/**
 * Turns the bytes of a document into text the editor and parser can work with, and
 * back again without changing anything the user did not change.
 *
 * Pure Kotlin on purpose: none of this needs Android, so it is unit tested on the JVM.
 */
object DocumentCodec {

    /** Bytes to inspect when deciding whether a file is text at all. */
    private const val SNIFF_LENGTH = 8 * 1024

    private const val BOM = '\uFEFF'

    /** How a file separated its lines, so saving can put the same ones back. */
    enum class LineEndings(val sequence: String) {
        Lf("\n"),
        CrLf("\r\n"),
        Cr("\r"),
    }

    data class Decoded(val text: String, val lineEndings: LineEndings, val hadBom: Boolean)

    /**
     * Decodes [bytes] as UTF-8 and normalises it for editing.
     *
     * A leading byte-order mark is stripped: left in place it is an ordinary character
     * as far as the parser is concerned, so `# Title` at the start of a BOM'd file is
     * a paragraph rather than a heading. Windows editors write BOMs routinely, so this
     * is the common case, not an edge case.
     *
     * Line endings are normalised to `\n` because Compose's text field treats a CRLF
     * as two characters, which makes cursor movement and selection behave oddly. The
     * original ending is recorded so [encode] can restore it.
     */
    fun decode(bytes: ByteArray): Decoded {
        val raw = bytes.toString(Charsets.UTF_8)
        val hadBom = raw.startsWith(BOM)
        val body = if (hadBom) raw.substring(1) else raw

        val endings = when {
            body.contains("\r\n") -> LineEndings.CrLf
            body.contains('\r') -> LineEndings.Cr
            else -> LineEndings.Lf
        }

        val normalised = when (endings) {
            LineEndings.Lf -> body
            LineEndings.CrLf -> body.replace("\r\n", "\n")
            LineEndings.Cr -> body.replace('\r', '\n')
        }

        return Decoded(normalised, endings, hadBom)
    }

    /**
     * Encodes [text] back to UTF-8, restoring the [lineEndings] and byte-order mark the
     * file arrived with. A document opened on Windows and saved from here should still
     * look untouched to the tools that produced it.
     */
    fun encode(
        text: String,
        lineEndings: LineEndings = LineEndings.Lf,
        withBom: Boolean = false,
    ): ByteArray {
        val body = if (lineEndings == LineEndings.Lf) text else text.replace("\n", lineEndings.sequence)
        val prefixed = if (withBom) BOM + body else body
        return prefixed.toByteArray(Charsets.UTF_8)
    }

    /**
     * True when [bytes] look like something other than text.
     *
     * A NUL byte never appears in valid UTF-8 text, so it is a reliable signal that the
     * user picked, say, a PDF from the document picker -- worth refusing before the
     * whole thing is decoded into a text field.
     */
    fun looksBinary(bytes: ByteArray): Boolean =
        bytes.take(SNIFF_LENGTH).any { it == 0.toByte() }
}
