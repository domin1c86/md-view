package com.mdview.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * How the app moves.
 *
 * The colour and shape half of the design system has lived in [Skin] for a while; this is
 * the half that was missing. Before it, a search of the whole source tree for `tween`,
 * `Easing` or `animationSpec` returned nothing at all -- every piece of movement in the
 * app was a Material or platform default that nobody had chosen.
 *
 * **Deliberately not part of a skin.** `docs/SKINS.md` tells authors a skin controls
 * colour, corner radius and type weight and explicitly *not* animation, and that is a
 * promise worth keeping: eight built-in skins sharing one motion language is a coherent
 * app, whereas eight motion languages is eight apps. These are app-level constants and
 * there is no `LocalMotion`.
 *
 * **What this does not reach.** Material 3 1.4.0 has a `MotionScheme` that its own
 * components resolve their timing through -- `DropdownMenu`, `Switch`, `RadioButton`, the
 * navigation indicators -- but the type is `internal`, so it cannot be implemented or
 * passed to `MaterialTheme` from outside the library. Those components keep Material's
 * timing until it is made public, at which point this object gains a `Scheme` and
 * `MdViewTheme` gains one argument. Their *appearance* is already ours; see
 * `ui/SkinOverlays.kt`.
 *
 * Everything here is finite. An indefinitely repeating animation would hang
 * `ComposeTestRule.waitForIdle`, which is what every instrumented suite is built on.
 */
object MdViewMotion {

    /** Symmetric, for anything that changes in place: colour, alpha, a selection state. */
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /**
     * Front-loaded, for anything arriving. Most of the distance is covered early and the
     * last of it settles, which is what makes an entrance read as fast without being
     * abrupt.
     */
    val Enter = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** The mirror of [Enter]. Departures start gently and leave quickly. */
    val Exit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /**
     * Durations in milliseconds.
     *
     * [Fast] is also roughly where Material's own snackbar fade sits, which is not a
     * coincidence -- that one animation cannot be retimed either (see `SkinSnackbar`), so
     * the scale is chosen to sit beside it rather than clash with it.
     */
    const val Fast = 120
    const val Default = 220
    const val Slow = 340

    /** Colour, alpha, and anything else with no geometry to settle. */
    fun <T> effects(): FiniteAnimationSpec<T> = tween(Fast, easing = Standard)

    /** The document arriving from the dashboard, or the dashboard coming back. */
    fun screenEnter(forward: Boolean): EnterTransition =
        slideInHorizontally(tween(Default, easing = Enter)) { width ->
            if (forward) width / SCREEN_TRAVEL else -width / SCREEN_TRAVEL
        } + fadeIn(tween(Default, easing = Standard))

    /** The screen being left behind. Shorter than the entrance, so the two do not queue. */
    fun screenExit(forward: Boolean): ExitTransition =
        slideOutHorizontally(tween(Default, easing = Exit)) { width ->
            if (forward) -width / SCREEN_TRAVEL else width / SCREEN_TRAVEL
        } + fadeOut(tween(Fast, easing = Exit))

    /** A dialog panel arriving: it grows the last few percent rather than flying in. */
    val dialogEnter: EnterTransition =
        fadeIn(tween(Default, easing = Standard)) +
            scaleIn(tween(Default, easing = Enter), initialScale = 0.92f)

    val dialogExit: ExitTransition =
        fadeOut(tween(Fast, easing = Exit)) +
            scaleOut(tween(Fast, easing = Exit), targetScale = 0.96f)

    /**
     * A screen moves an eighth of its width, not a whole one.
     *
     * A full-width slide on a phone is a long way for a 220 ms animation to travel and
     * reads as a lurch; the short offset plus the fade carries the direction on its own.
     */
    private const val SCREEN_TRAVEL = 8
}
