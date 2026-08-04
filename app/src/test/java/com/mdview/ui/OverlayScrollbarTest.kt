package com.mdview.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scroll indicator's geometry.
 *
 * Everything the indicator does that can be wrong without looking wrong is arithmetic:
 * a thumb that stops a few pixels short of the end, or one that leaves the track
 * entirely at the extremes, is the kind of thing nobody notices until it is pointed out.
 * The drawing around it needs a device; this does not.
 */
class OverlayScrollbarTest {

    @Test
    fun contentThatFitsHasNoThumb() {
        assertNull(thumb(content = VIEWPORT))
        // A sub-pixel overflow is a rounding artefact from a measured layout, not a
        // scrollable document, and an indicator for it would flicker on and off.
        assertNull(thumb(content = VIEWPORT + 0.4f))
    }

    @Test
    fun aDegenerateContainerHasNoThumb() {
        // Both happen for a frame or two before the first measure lands.
        assertNull(scrollbarThumb(0f, 4000f, 0f, TRACK, MIN))
        assertNull(scrollbarThumb(VIEWPORT, 4000f, 0f, track = 0f, minLength = MIN))
    }

    @Test
    fun theThumbIsTheViewportsShareOfTheTrack() {
        // A quarter of the document on screen, so a quarter of the track.
        val thumb = requireNotNull(thumb(content = VIEWPORT * 4))
        assertEquals(TRACK / 4, thumb.length, TOLERANCE)
    }

    @Test
    fun theThumbTouchesBothEndsExactly() {
        val content = VIEWPORT * 4
        val overflow = content - VIEWPORT

        val atRest = requireNotNull(thumb(content = content, scrolled = 0f))
        assertEquals("did not start at the top of the track", 0f, atRest.top, TOLERANCE)

        val atEnd = requireNotNull(thumb(content = content, scrolled = overflow))
        assertEquals(
            "did not finish flush with the end of the track",
            TRACK,
            atEnd.top + atEnd.length,
            TOLERANCE,
        )
    }

    @Test
    fun halfwayThroughIsHalfwayDown() {
        val content = VIEWPORT * 4
        val thumb = requireNotNull(thumb(content = content, scrolled = (content - VIEWPORT) / 2))
        assertEquals((TRACK - thumb.length) / 2, thumb.top, TOLERANCE)
    }

    @Test
    fun aVeryLongDocumentKeepsAVisibleThumb() {
        // Two hundred screens: the honest share of the track is under 4 px.
        val content = VIEWPORT * 200
        val atRest = requireNotNull(thumb(content = content, scrolled = 0f))
        assertEquals("the floor did not apply", MIN, atRest.length, TOLERANCE)

        // The floor stretches the thumb past its true share, so an offset scaled from the
        // scroll position alone would now overshoot the track. It must not.
        val atEnd = requireNotNull(thumb(content = content, scrolled = content - VIEWPORT))
        assertEquals(TRACK, atEnd.top + atEnd.length, TOLERANCE)
    }

    @Test
    fun aTrackShorterThanTheFloorIsStillDrawable() {
        // A short editor with the keyboard up. The thumb fills the track rather than
        // hanging off the end of it -- and coercing to an empty range would throw.
        val thumb = requireNotNull(scrollbarThumb(200f, 4000f, 100f, track = 20f, minLength = MIN))
        assertEquals(20f, thumb.length, TOLERANCE)
        assertEquals(0f, thumb.top, TOLERANCE)
    }

    @Test
    fun overscrollStaysInsideTheTrack() {
        val content = VIEWPORT * 4
        val overflow = content - VIEWPORT

        // Both ends of a stretch overscroll, where the reported offset briefly leaves the
        // range the layout said it had.
        val before = requireNotNull(thumb(content = content, scrolled = -300f))
        assertEquals(0f, before.top, TOLERANCE)

        val past = requireNotNull(thumb(content = content, scrolled = overflow + 300f))
        assertTrue(
            "thumb left the track: ${past.top} + ${past.length} > $TRACK",
            past.top + past.length <= TRACK + TOLERANCE,
        )
    }

    private fun thumb(content: Float, scrolled: Float = 0f) =
        scrollbarThumb(VIEWPORT, content, scrolled, TRACK, MIN)

    private companion object {
        const val VIEWPORT = 2000f
        const val TRACK = 1960f
        const val MIN = 96f
        const val TOLERANCE = 0.01f
    }
}
