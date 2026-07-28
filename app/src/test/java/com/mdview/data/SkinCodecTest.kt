package com.mdview.data

import androidx.compose.ui.graphics.Color
import com.mdview.ui.theme.BuiltInSkins
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinCodecTest {

    private fun reasonOf(text: String): InvalidSkinException.Reason? =
        runCatching { SkinCodec.decode(text) }
            .exceptionOrNull()
            .let { it as? InvalidSkinException }
            ?.reason

    private val minimal = """{"id":"custom","name":"Custom","base":"ink"}"""

    // -- colour parsing ---------------------------------------------------------------

    @Test
    fun `three digit hex expands each nibble`() {
        assertEquals(Color(0xFFAABBCC), SkinCodec.parseColor("#abc"))
    }

    @Test
    fun `six digit hex is opaque`() {
        assertEquals(Color(0xFF123456), SkinCodec.parseColor("#123456"))
    }

    @Test
    fun `eight digit hex keeps its alpha`() {
        assertEquals(Color(0x80123456), SkinCodec.parseColor("#80123456"))
    }

    @Test
    fun `colour parsing ignores case`() {
        assertEquals(SkinCodec.parseColor("#AABBCC"), SkinCodec.parseColor("#aabbcc"))
    }

    @Test
    fun `nonsense colours are refused rather than guessed at`() {
        listOf("#GGG", "red", "#12345", "", "#", "0x123456").forEach { value ->
            assertNull("expected null for $value", SkinCodec.parseColor(value))
        }
    }

    @Test
    fun `an opaque colour formats without an alpha channel`() {
        assertEquals("#123456", SkinCodec.formatColor(Color(0xFF123456)))
    }

    @Test
    fun `a translucent colour keeps its alpha when formatted`() {
        assertEquals("#80123456", SkinCodec.formatColor(Color(0x80123456)))
    }

    // -- inheritance ------------------------------------------------------------------

    @Test
    fun `a three key skin inherits every token from its base`() {
        val skin = SkinCodec.decode(minimal)

        assertEquals(BuiltInSkins.Ink.colors, skin.colors)
        assertEquals(BuiltInSkins.Ink.shape, skin.shape)
        assertTrue(skin.dark)
    }

    @Test
    fun `an overridden token applies while its siblings still inherit`() {
        val skin = SkinCodec.decode(
            """{"id":"custom","name":"Custom","base":"ink","colors":{"accent":"#FF0000"}}"""
        )

        assertEquals(Color(0xFFFF0000), skin.colors.accent)
        assertEquals(BuiltInSkins.Ink.colors.canvas, skin.colors.canvas)
    }

    @Test
    fun `a malformed colour costs one token rather than the group`() {
        val skin = SkinCodec.decode(
            """{"id":"c","name":"C","base":"paper","colors":{"accent":"nope","link":"#00FF00"}}"""
        )

        assertEquals(BuiltInSkins.Paper.colors.accent, skin.colors.accent)
        assertEquals(Color(0xFF00FF00), skin.colors.link)
    }

    @Test
    fun `an absent base defaults by declared darkness`() {
        assertEquals(
            BuiltInSkins.Ink.colors.canvas,
            SkinCodec.decode("""{"id":"c","name":"C","dark":true}""").colors.canvas,
        )
        assertEquals(
            BuiltInSkins.Paper.colors.canvas,
            SkinCodec.decode("""{"id":"c","name":"C"}""").colors.canvas,
        )
    }

    @Test
    fun `darkness is inherited from the base when not declared`() {
        assertTrue(SkinCodec.decode("""{"id":"c","name":"C","base":"midnight"}""").dark)
    }

    // -- forward compatibility --------------------------------------------------------

    @Test
    fun `unknown keys are ignored rather than fatal`() {
        val skin = SkinCodec.decode(
            """{"id":"c","name":"C","base":"ink","motto":"hi","colors":{"nope":"#FFF"}}"""
        )

        assertEquals("c", skin.id)
    }

    @Test
    fun `a newer schema still parses`() {
        assertNotNull(SkinCodec.decode("""{"schema":99,"id":"c","name":"C","base":"ink"}"""))
    }

    @Test
    fun `arrays are skipped rather than rejected`() {
        assertNotNull(
            SkinCodec.decode("""{"id":"c","name":"C","base":"ink","tags":["a","b",[1,2]]}""")
        )
    }

    @Test
    fun `a byte order mark is stripped instead of failing as malformed`() {
        // Written as an escape, never as a literal byte: lint fails the build on one.
        assertEquals("custom", SkinCodec.decode("\uFEFF" + minimal).id)
    }

    // -- validation -------------------------------------------------------------------

    @Test
    fun `an id that could escape the skins directory is refused`() {
        listOf("../evil", "a/b", "", "has space", "über", "-leading", "under_score").forEach { id ->
            assertEquals(
                "expected BadId for '$id'",
                InvalidSkinException.Reason.BadId,
                reasonOf("""{"id":"$id","name":"C","base":"ink"}"""),
            )
        }
    }

    @Test
    fun `an uppercase id is normalised rather than refused`() {
        // The id becomes a filename, and case-insensitive filesystems would let "Mine"
        // and "mine" collide. Folding is friendlier than rejecting and just as safe,
        // since the charset is still enforced afterwards.
        assertEquals("mine", SkinCodec.decode("""{"id":"Mine","name":"C","base":"ink"}""").id)
    }

    @Test
    fun `an id longer than the limit is refused`() {
        val id = "a".repeat(65)
        assertEquals(
            InvalidSkinException.Reason.BadId,
            reasonOf("""{"id":"$id","name":"C","base":"ink"}"""),
        )
    }

    @Test
    fun `a missing name is refused`() {
        assertEquals(
            InvalidSkinException.Reason.BadName,
            reasonOf("""{"id":"c","name":"   ","base":"ink"}"""),
        )
    }

    @Test
    fun `a base this app does not ship is refused`() {
        assertEquals(
            InvalidSkinException.Reason.UnknownBase,
            reasonOf("""{"id":"c","name":"C","base":"vaporwave"}"""),
        )
    }

    @Test
    fun `things that are not json are refused`() {
        listOf(
            "",
            "not json",
            "{",
            """{"id":"c",}""",
            """{'id':'c'}""",
            """{id:"c"}""",
            """{"id":"c" "name":"C"}""",
            """{"id":"c","name":"C","dark":NaN}""",
            "[1,2,3]",
            """{"id":"c","name":"C"} trailing""",
        ).forEach { text ->
            assertEquals(
                "expected Malformed for: $text",
                InvalidSkinException.Reason.Malformed,
                reasonOf(text),
            )
        }
    }

    @Test
    fun `a file over the size cap is refused before it is parsed`() {
        val padding = "x".repeat(SkinCodec.MAX_CHARS + 1)
        assertEquals(InvalidSkinException.Reason.TooLarge, reasonOf(padding))
    }

    @Test
    fun `nesting beyond the depth limit is refused`() {
        val deep = """{"id":"c","name":"C","base":"ink","a":""" +
            "{\"b\":".repeat(12) + "1" + "}".repeat(12) + "}"

        assertEquals(InvalidSkinException.Reason.TooLarge, reasonOf(deep))
    }

    @Test
    fun `a string longer than the limit is refused`() {
        val long = "y".repeat(300)
        assertEquals(
            InvalidSkinException.Reason.TooLarge,
            reasonOf("""{"id":"c","name":"C","base":"ink","author":"$long"}"""),
        )
    }

    @Test
    fun `escape sequences survive parsing`() {
        val skin = SkinCodec.decode(
            """{"id":"c","name":"A \"quoted\"\tname","base":"ink","author":"xéy"}"""
        )

        assertEquals("A \"quoted\"\tname", skin.name)
        assertEquals("xéy", skin.author)
    }

    // -- clamping ---------------------------------------------------------------------

    @Test
    fun `out of range numbers are clamped rather than rejected`() {
        val skin = SkinCodec.decode(
            """{"id":"c","name":"C","base":"ink",
               "shape":{"small":-5,"medium":900,"large":20},
               "type":{"bodyScale":9.0,"monoScale":0.0,"headingWeight":42,"tracking":5.0}}"""
        )

        assertEquals(0, skin.shape.small)
        assertEquals(48, skin.shape.medium)
        assertEquals(1.3f, skin.type.bodyScale, 0.001f)
        assertEquals(0.7f, skin.type.monoScale, 0.001f)
        assertEquals(100, skin.type.headingWeight)
        assertEquals(0.1f, skin.type.tracking, 0.001f)
    }

    @Test
    fun `a heading weight is snapped to a hundred`() {
        val skin = SkinCodec.decode(
            """{"id":"c","name":"C","base":"ink","type":{"headingWeight":651}}"""
        )

        assertEquals(600, skin.type.headingWeight)
    }

    // -- round trip -------------------------------------------------------------------

    @Test
    fun `every built-in survives an encode and decode`() {
        BuiltInSkins.all.forEach { skin ->
            assertEquals(skin, SkinCodec.decode(SkinCodec.encode(skin)))
        }
    }

    @Test
    fun `an encoded skin carries no base, so retuning a built-in cannot move it`() {
        val encoded = SkinCodec.encode(SkinCodec.decode(minimal))

        assertTrue("\"base\"" !in encoded)
        assertEquals(BuiltInSkins.Ink.colors.canvas, SkinCodec.decode(encoded).colors.canvas)
    }

    @Test
    fun `an id is accepted only in the documented charset`() {
        assertTrue(SkinCodec.isValidId("solar-flare-2"))
        assertTrue(SkinCodec.isValidId("a"))
        listOf("-leading", "Upper", "under_score", "a".repeat(65), "").forEach {
            assertTrue("expected '$it' to be invalid", !SkinCodec.isValidId(it))
        }
    }
}
