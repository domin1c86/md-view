package com.mdview.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.mdview.ui.theme.LocalSkin
import com.mdview.ui.theme.MdViewMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop

/**
 * A scroll indicator in the style of the platform's on iOS and macOS: a thin capsule at
 * the trailing edge that appears while the content is moving and fades out a second after
 * it stops.
 *
 * Three things about it are deliberate.
 *
 * **It is drawn, not laid out.** This is `drawWithContent` on the scroll container itself,
 * so the indicator costs no width at all: everything underneath is measured exactly as it
 * was before it existed, and a document still uses the full reading column. A real
 * scrollbar track would have reflowed every screen in the app to reserve space for a
 * decoration that is invisible most of the time.
 *
 * **It is an indicator, not a control.** There is no `pointerInput` anywhere here, so it
 * never competes for a gesture — which matters most in the preview, where the whole
 * document sits inside a `SelectionContainer` and a drag already means "select text".
 *
 * **It has no skin token of its own.** [com.mdview.ui.theme.SkinColors.textMuted] is the
 * quietest readable colour a skin publishes and it is already held to a contrast floor
 * against all four surfaces, which is exactly what this needs. Adding a token instead
 * would have widened `docs/SKINS.md`'s published contract — token names are public API —
 * for one hairline.
 *
 * Both overloads take the scroll state the container is already using, so the indicator
 * follows a caret-driven or programmatic scroll as readily as a finger.
 */
@Composable
fun Modifier.overlayScrollbar(state: LazyListState): Modifier = overlayScrollbar(
    key = state,
    signal = {
        ScrollSignal(
            state.firstVisibleItemIndex,
            state.firstVisibleItemScrollOffset,
            state.isScrollInProgress,
        )
    },
    // The lazy list carries its own measurements, so the viewport it is handed is unused.
    extent = { state.extent() },
)

/** The [ScrollState] overload, for the editor and any plain `verticalScroll` container. */
@Composable
fun Modifier.overlayScrollbar(state: ScrollState): Modifier = overlayScrollbar(
    key = state,
    signal = { ScrollSignal(state.value, 0, state.isScrollInProgress) },
    extent = { viewport -> state.extent(viewport) },
)

/**
 * What "the content moved" looks like as a value.
 *
 * The position is in it because a scroll driven by anything other than a finger — the
 * caret in the editor, a restored position — never sets `isScrollInProgress`, and
 * an indicator that only answered to gestures would sit out exactly those cases.
 */
private data class ScrollSignal(val index: Int, val offset: Int, val active: Boolean)

/** How far the content reaches, in pixels along the scrolling axis. */
private class Extent(val scrolled: Float, val content: Float)

@Composable
private fun Modifier.overlayScrollbar(
    key: Any,
    signal: () -> ScrollSignal,
    extent: (viewport: Float) -> Extent?,
): Modifier {
    // An Animatable driven from the effect rather than `animateFloatAsState`, because the
    // latter needs its target read during composition -- which would recompose whatever
    // this modifier is attached to twice per scroll. Nothing here is read in composition
    // at all, so a scroll costs drawing and nothing else.
    val fade = remember(key) { Animatable(0f) }

    LaunchedEffect(key) {
        snapshotFlow(signal)
            // snapshotFlow emits where the content already is the moment it is collected,
            // which is not a scroll. Without this the indicator flashes for a second every
            // time a screen opens.
            .drop(1)
            .collectLatest { moment ->
                // Asymmetric on purpose: there before the content has visibly moved, and
                // leaving slowly enough to read as receding rather than switched off.
                fade.animateTo(1f, tween(MdViewMotion.Fast, easing = MdViewMotion.Standard))
                // collectLatest cancels this block on the next change, so the wait is
                // always measured from the last one. A finger held still mid-drag produces
                // no change at all, which is why the timer is not started while the
                // gesture is live -- otherwise the indicator would vanish under the thumb.
                if (!moment.active) {
                    delay(HIDE_AFTER_MS)
                    fade.animateTo(0f, tween(MdViewMotion.Default, easing = MdViewMotion.Exit))
                }
            }
    }

    val color = LocalSkin.current.colors.textMuted

    return drawWithContent {
        drawContent()

        // Every read below happens in the draw phase, so a fade or a scroll costs a frame
        // of drawing rather than a recomposition of whatever this is attached to -- which,
        // on the preview, is the whole document.
        val alpha = fade.value
        if (alpha <= 0f) return@drawWithContent

        val inset = TRACK_INSET.toPx()
        val reach = extent(size.height) ?: return@drawWithContent
        val thumb = scrollbarThumb(
            viewport = size.height,
            content = reach.content,
            scrolled = reach.scrolled,
            track = size.height - inset * 2,
            minLength = MIN_THUMB.toPx(),
        ) ?: return@drawWithContent

        val width = THUMB_WIDTH.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - width - EDGE_GAP.toPx(), inset + thumb.top),
            size = Size(width, thumb.length),
            cornerRadius = CornerRadius(width / 2f),
            alpha = alpha * THUMB_OPACITY,
        )
    }
}

/**
 * The extent of a lazy list, **estimated**.
 *
 * A `LazyColumn` only knows the size of the items it has measured, so the total height of
 * a list of unequal cards is not a number anyone can have. The mean of the visible items
 * stands in for the rest. The visible consequence is that the thumb's length breathes a
 * little as differently sized cards pass through; its two ends stay exact, because
 * [scrollbarThumb] positions it by progress rather than by this estimate.
 */
private fun LazyListState.extent(): Extent? {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return null

    val pitch = visible.sumOf { it.size }.toFloat() / visible.size + info.mainAxisItemSpacing
    if (pitch <= 0f) return null

    return Extent(
        scrolled = firstVisibleItemIndex * pitch + firstVisibleItemScrollOffset,
        content = pitch * info.totalItemsCount +
            info.beforeContentPadding + info.afterContentPadding,
    )
}

/** The same for a plain scroller, where the answer is exact rather than estimated. */
private fun ScrollState.extent(viewport: Float): Extent? {
    // maxValue is Int.MAX_VALUE until the container has been measured once.
    if (maxValue <= 0 || maxValue == Int.MAX_VALUE) return null
    return Extent(scrolled = value.toFloat(), content = viewport + maxValue)
}

/** Where the thumb sits along the track, in pixels from the track's start. */
internal class ScrollbarThumb(val top: Float, val length: Float)

/**
 * The whole geometry of the indicator, as arithmetic — which is why it is a function
 * rather than four expressions inside the draw block, and why `OverlayScrollbarTest` can
 * cover it on the JVM.
 *
 * Two decisions live here. The thumb has a floor of [minLength] so a very long document
 * cannot shrink it to a dot; and its offset is driven by *progress* through the scroll
 * range rather than by scaling [scrolled] directly, which is what makes it touch both
 * ends of the track exactly even once that floor has stretched it past its true share.
 *
 * Returns null when there is nothing to indicate.
 */
internal fun scrollbarThumb(
    viewport: Float,
    content: Float,
    scrolled: Float,
    track: Float,
    minLength: Float,
): ScrollbarThumb? {
    val overflow = content - viewport
    // Under a pixel of overflow is a rounding artefact, not a scrollable document.
    if (viewport <= 0f || track <= 0f || overflow <= 1f) return null

    val length = (viewport / content * track).coerceIn(minLength.coerceAtMost(track), track)
    val progress = (scrolled / overflow).coerceIn(0f, 1f)
    return ScrollbarThumb(top = progress * (track - length), length = length)
}

/** Narrow enough to read as a hairline; the corner radius is half of it, so it is a capsule. */
private val THUMB_WIDTH = 3.dp

/** Between the thumb and the trailing edge. */
private val EDGE_GAP = 2.dp

/** Trimmed off both ends of the track, so the thumb never touches a corner. */
private val TRACK_INSET = 4.dp

/** A floor, so a very long document still has a thumb rather than a speck. */
private val MIN_THUMB = 32.dp

/** Muted rather than solid: this sits on top of text it must not compete with. */
private const val THUMB_OPACITY = 0.55f

/** Measured from the last movement, not from the end of the gesture. */
private const val HIDE_AFTER_MS = 1000L
