package com.mdview.markdown

import com.mdview.markdown.DocumentCodec.LineEndings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentCodecTest {

    private fun bytes(text: String) = text.toByteArray(Charsets.UTF_8)

    @Test
    fun plainTextIsUnchanged() {
        val decoded = DocumentCodec.decode(bytes("# Title\n\nBody\n"))

        assertEquals("# Title\n\nBody\n", decoded.text)
        assertEquals(LineEndings.Lf, decoded.lineEndings)
        assertFalse(decoded.hadBom)
    }

    @Test
    fun byteOrderMarkIsStripped() {
        val decoded = DocumentCodec.decode(bytes("\uFEFF# Title\n"))

        // Left in place the BOM is just another character, and `# Title` preceded by
        // one is a paragraph rather than a heading.
        assertEquals("# Title\n", decoded.text)
        assertTrue(decoded.hadBom)
    }

    @Test
    fun aStrippedBomComesBackOnEncode() {
        val decoded = DocumentCodec.decode(bytes("\uFEFFhello\n"))
        val encoded = DocumentCodec.encode(decoded.text, decoded.lineEndings, decoded.hadBom)

        assertArrayEquals(bytes("\uFEFFhello\n"), encoded)
    }

    @Test
    fun windowsLineEndingsAreNormalisedForEditing() {
        val decoded = DocumentCodec.decode(bytes("one\r\ntwo\r\n"))

        assertEquals("one\ntwo\n", decoded.text)
        assertEquals(LineEndings.CrLf, decoded.lineEndings)
    }

    @Test
    fun windowsLineEndingsSurviveARoundTrip() {
        val original = bytes("one\r\ntwo\r\nthree\r\n")
        val decoded = DocumentCodec.decode(original)

        assertArrayEquals(
            original,
            DocumentCodec.encode(decoded.text, decoded.lineEndings, decoded.hadBom),
        )
    }

    @Test
    fun classicMacLineEndingsAreRecognised() {
        val decoded = DocumentCodec.decode(bytes("one\rtwo\r"))

        assertEquals("one\ntwo\n", decoded.text)
        assertEquals(LineEndings.Cr, decoded.lineEndings)
    }

    @Test
    fun editsMadeAgainstCrlfContentAreWrittenBackAsCrlf() {
        val decoded = DocumentCodec.decode(bytes("one\r\ntwo\r\n"))
        val edited = decoded.text + "three\n"

        assertArrayEquals(
            bytes("one\r\ntwo\r\nthree\r\n"),
            DocumentCodec.encode(edited, decoded.lineEndings, decoded.hadBom),
        )
    }

    @Test
    fun textIsNotMistakenForBinary() {
        assertFalse(DocumentCodec.looksBinary(bytes("# Perfectly ordinary Markdown\n")))
    }

    @Test
    fun nulBytesMarkAFileAsBinary() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00)

        assertTrue(DocumentCodec.looksBinary(png))
    }

    @Test
    fun emptyContentIsHandled() {
        val decoded = DocumentCodec.decode(ByteArray(0))

        assertEquals("", decoded.text)
        assertEquals(LineEndings.Lf, decoded.lineEndings)
        assertFalse(DocumentCodec.looksBinary(ByteArray(0)))
    }
}
