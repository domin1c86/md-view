package com.mdview

import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mdview.ui.theme.BuiltInSkins
import com.mdview.ui.theme.LocalMonoTextStyle
import com.mdview.ui.theme.LocalSkin
import com.mdview.ui.theme.MdViewTheme
import com.mdview.ui.theme.ReadingTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the one sharp edge in the theming layer.
 *
 * `ReadingTypography` re-invokes `MaterialTheme`, and anything passed as a *parameter*
 * there is silently dropped unless it is re-forwarded. These assertions arm that trap
 * rather than leaving it as a comment for someone to read after the fact.
 */
@RunWith(AndroidJUnit4::class)
class ThemeInvariantTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theSkinSurvivesTheNestedThemeInsideReadingTypography() {
        var outside: String? = null
        var inside: String? = null

        rule.setContent {
            MdViewTheme(skin = BuiltInSkins.Midnight) {
                outside = LocalSkin.current.id
                ReadingTypography(scale = 1.25f) {
                    inside = LocalSkin.current.id
                }
            }
        }

        assertEquals(BuiltInSkins.Midnight.id, outside)
        assertEquals("the skin was lost inside ReadingTypography", outside, inside)
    }

    @Test
    fun colourAndShapeAreReForwardedThroughTheNestedTheme() {
        var outsideSurface: Any? = null
        var insideSurface: Any? = null
        var outsideShape: Any? = null
        var insideShape: Any? = null

        rule.setContent {
            MdViewTheme(skin = BuiltInSkins.Nord) {
                outsideSurface = MaterialTheme.colorScheme.surface
                outsideShape = MaterialTheme.shapes.medium
                ReadingTypography(scale = 1.25f) {
                    insideSurface = MaterialTheme.colorScheme.surface
                    insideShape = MaterialTheme.shapes.medium
                }
            }
        }

        assertEquals(BuiltInSkins.Nord.colors.surface, outsideSurface)
        assertEquals(outsideSurface, insideSurface)
        assertEquals(outsideShape, insideShape)
    }

    @Test
    fun pressFeedbackComesFromTheSkinAndSurvivesTheNestedTheme() {
        // The ripple is delivered as a CompositionLocal rather than as a MaterialTheme
        // argument, which is precisely why it survives ReadingTypography without being
        // re-forwarded -- the opposite of colourAndShapeAreReForwardedThroughTheNestedTheme
        // above. Both halves of that rule are worth holding down.
        var outside: RippleConfiguration? = null
        var inside: RippleConfiguration? = null

        rule.setContent {
            MdViewTheme(skin = BuiltInSkins.Cobalt) {
                outside = LocalRippleConfiguration.current
                ReadingTypography(scale = 1.25f) {
                    inside = LocalRippleConfiguration.current
                }
            }
        }

        assertEquals(BuiltInSkins.Cobalt.colors.accent, outside?.color)
        assertEquals("the ripple was lost inside ReadingTypography", outside, inside)
    }

    @Test
    fun theReadingScaleReachesMonospaceTextToo() {
        // The editor and code blocks used to hardcode their size, so the reading-size
        // preference worked in prose and did nothing at all in code.
        var plain = 0f
        var scaled = 0f

        rule.setContent {
            MdViewTheme(skin = BuiltInSkins.Paper) {
                ReadingTypography(scale = 1f) { plain = LocalMonoTextStyle.current.fontSize.value }
                ReadingTypography(scale = 1.5f) { scaled = LocalMonoTextStyle.current.fontSize.value }
            }
        }

        assertTrue("mono text did not scale: $plain -> $scaled", scaled > plain * 1.4f)
    }
}
