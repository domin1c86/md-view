package com.mdview.ui.theme

import androidx.compose.ui.graphics.Color
import com.mdview.data.SkinCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Holds the shipped palettes to the contrast floors that `docs/SKINS.md` publishes.
 *
 * Asserted in code rather than checked once by hand: the numbers are the whole reason a
 * skin author can trust the built-ins as examples, and a later "just darken that grey a
 * little" is exactly the kind of change that breaks them silently.
 */
class BuiltInSkinsTest {

    /** WCAG 2.x relative luminance. */
    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (x, y) = luminance(a) to luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    private fun assertContrast(skin: Skin, name: String, fg: Color, bg: Color, floor: Double) {
        val ratio = contrast(fg, bg)
        assertTrue(
            "${skin.id}: $name is ${"%.2f".format(ratio)}:1, needs $floor:1",
            ratio >= floor,
        )
    }

    /** The four rungs any foreground token might land on. */
    private fun surfaces(skin: Skin) = listOf(
        "canvas" to skin.colors.canvas,
        "surface" to skin.colors.surface,
        "surfaceRaised" to skin.colors.surfaceRaised,
        "surfaceSunken" to skin.colors.surfaceSunken,
    )

    @Test
    fun `body and supporting text clear AA on every surface rung`() {
        BuiltInSkins.all.forEach { skin ->
            surfaces(skin).forEach { (surfaceName, background) ->
                assertContrast(skin, "textPrimary on $surfaceName", skin.colors.textPrimary, background, 4.5)
                assertContrast(skin, "textSecondary on $surfaceName", skin.colors.textSecondary, background, 4.5)
                assertContrast(skin, "quoteText on $surfaceName", skin.colors.quoteText, background, 4.5)
                assertContrast(skin, "link on $surfaceName", skin.colors.link, background, 4.5)
                assertContrast(skin, "linkPressed on $surfaceName", skin.colors.linkPressed, background, 4.5)
            }
        }
    }

    @Test
    fun `muted and status colours clear the non-text floor everywhere`() {
        BuiltInSkins.all.forEach { skin ->
            surfaces(skin).forEach { (surfaceName, background) ->
                assertContrast(skin, "textMuted on $surfaceName", skin.colors.textMuted, background, 3.0)
                assertContrast(skin, "danger on $surfaceName", skin.colors.danger, background, 3.0)
                assertContrast(skin, "success on $surfaceName", skin.colors.success, background, 3.0)
            }
        }
    }

    @Test
    fun `the accent carries its own label and reads as text on the two bars`() {
        BuiltInSkins.all.forEach { skin ->
            assertContrast(skin, "onAccent on accent", skin.colors.onAccent, skin.colors.accent, 4.5)
            assertContrast(skin, "accent on canvas", skin.colors.accent, skin.colors.canvas, 3.0)
            assertContrast(skin, "accent on surface", skin.colors.accent, skin.colors.surface, 3.0)
        }
    }

    @Test
    fun `code is readable on its own background`() {
        BuiltInSkins.all.forEach { skin ->
            assertContrast(skin, "code on codeBackground", skin.colors.code, skin.colors.codeBackground, 4.5)
        }
    }

    @Test
    fun `hairlines are visible against what they sit on`() {
        BuiltInSkins.all.forEach { skin ->
            assertContrast(skin, "border on canvas", skin.colors.border, skin.colors.canvas, 1.2)
            assertContrast(skin, "border on surface", skin.colors.border, skin.colors.surface, 1.2)
            assertContrast(
                skin,
                "tableBorder on tableHeader",
                skin.colors.tableBorder,
                skin.colors.tableHeader,
                1.2,
            )
        }
    }

    @Test
    fun `every colour token is fully opaque`() {
        // A translucent token composites against whatever is behind it, which would make
        // every ratio measured above a fiction. Imported skins may use alpha; these must
        // not. Read back off the encoded form so this covers all 23 without listing them.
        val colorValue = Regex("\"(#[0-9A-F]+)\"")
        BuiltInSkins.all.forEach { skin ->
            val body = SkinCodec.encode(skin).substringAfter("\"colors\"").substringBefore("\"shape\"")
            val values = colorValue.findAll(body).map { it.groupValues[1] }.toList()

            assertEquals("${skin.id} did not encode every colour", 23, values.size)
            values.forEach {
                assertEquals("${skin.id}: $it is not opaque #RRGGBB", 7, it.length)
            }
        }
    }

    @Test
    fun `ids are unique, valid and match the light and dark split`() {
        val ids = BuiltInSkins.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertTrue("$it is not a usable id", SkinCodec.isValidId(it)) }

        assertTrue(BuiltInSkins.all.any { !it.dark })
        assertTrue(BuiltInSkins.all.any { it.dark })
        assertEquals(BuiltInSkins.Paper, BuiltInSkins.default(dark = false))
        assertEquals(BuiltInSkins.Ink, BuiltInSkins.default(dark = true))
    }

    @Test
    fun `the derived scheme leaves no slot at its stock Material default`() {
        // The palette this replaced set twelve slots and let the rest fall through, which
        // is why menus and error text used to be stock purple in a blue app.
        BuiltInSkins.all.forEach { skin ->
            val scheme = skin.toColorScheme()
            assertEquals("${skin.id} background", skin.colors.canvas, scheme.background)
            assertEquals("${skin.id} surface", skin.colors.surface, scheme.surface)
            assertEquals("${skin.id} primary", skin.colors.accent, scheme.primary)
            assertEquals("${skin.id} tertiary", skin.colors.accent, scheme.tertiary)
            assertEquals("${skin.id} error", skin.colors.danger, scheme.error)
            assertEquals("${skin.id} outline", skin.colors.border, scheme.outline)
            assertEquals(
                "${skin.id} surfaceContainerHigh",
                skin.colors.surfaceRaised,
                scheme.surfaceContainerHigh,
            )
        }
    }

    @Test
    fun `the surface ladder is ordered, not just a set of four colours`() {
        BuiltInSkins.all.forEach { skin ->
            val sunken = luminance(skin.colors.surfaceSunken)
            val surface = luminance(skin.colors.surface)
            val raised = luminance(skin.colors.surfaceRaised)
            if (skin.dark) {
                assertTrue("${skin.id}: dark rungs must climb", sunken <= surface && surface <= raised)
            } else {
                assertTrue("${skin.id}: light rungs must not invert", sunken <= surface && surface <= raised)
            }
        }
    }
}
