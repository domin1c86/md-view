package com.mdview.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's motion tokens.
 *
 * Curves are the one part of a design system that is genuinely arithmetic, so they are
 * worth asserting rather than eyeballing: a cubic bezier with its control points
 * transposed still compiles, still animates, and is simply wrong in a way nobody notices
 * until it ships.
 */
class MotionTest {

    private val curves = listOf(
        "Standard" to MdViewMotion.Standard,
        "Enter" to MdViewMotion.Enter,
        "Exit" to MdViewMotion.Exit,
    )

    @Test
    fun everyCurveStartsAtRestAndArrivesExactly() {
        curves.forEach { (name, easing) ->
            assertEquals("$name did not start at 0", 0f, easing.transform(0f), TOLERANCE)
            assertEquals("$name did not reach 1", 1f, easing.transform(1f), TOLERANCE)
        }
    }

    @Test
    fun noCurveEverGoesBackwards() {
        // A non-monotonic easing reads as a stutter or a bounce. None of these is meant to
        // overshoot, so any decrease at all is a transposed control point.
        curves.forEach { (name, easing) ->
            var previous = 0f
            for (step in 0..STEPS) {
                val value = easing.transform(step.toFloat() / STEPS)
                assertTrue(
                    "$name went backwards at ${step.toFloat() / STEPS}: $previous -> $value",
                    value >= previous - TOLERANCE,
                )
                previous = value
            }
        }
    }

    @Test
    fun theCurvesLeanTheWayTheirNamesClaim() {
        // Enter covers most of its distance early and settles; Exit does the opposite.
        // This is the assertion that actually catches Enter and Exit being swapped, which
        // the two above cannot -- both are monotonic and both hit their endpoints.
        assertTrue(
            "Enter should be front-loaded, was ${MdViewMotion.Enter.transform(0.5f)}",
            MdViewMotion.Enter.transform(0.5f) > 0.7f,
        )
        assertTrue(
            "Exit should be back-loaded, was ${MdViewMotion.Exit.transform(0.5f)}",
            MdViewMotion.Exit.transform(0.5f) < 0.3f,
        )
    }

    @Test
    fun standardIsGentlerThanBothOfTheDirectionalCurves() {
        val standard = MdViewMotion.Standard.transform(0.5f)
        assertTrue(
            "Standard ($standard) should sit between Exit and Enter at the halfway point",
            standard > MdViewMotion.Exit.transform(0.5f) &&
                standard < MdViewMotion.Enter.transform(0.5f),
        )
    }

    @Test
    fun theDurationScaleIsOrderedAndPlausible() {
        assertTrue(
            "durations out of order: ${MdViewMotion.Fast}, ${MdViewMotion.Default}, ${MdViewMotion.Slow}",
            MdViewMotion.Fast < MdViewMotion.Default && MdViewMotion.Default < MdViewMotion.Slow,
        )
        // Below ~80 ms an animation is not perceived as motion, and past ~400 ms it is
        // perceived as waiting. The whole scale has to sit inside that.
        assertTrue("Fast is imperceptible", MdViewMotion.Fast >= 80)
        assertTrue("Slow is a wait, not a transition", MdViewMotion.Slow <= 400)
    }

    private companion object {
        const val TOLERANCE = 0.001f
        const val STEPS = 100
    }
}
