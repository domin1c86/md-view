package com.mdview.ui

import android.view.ViewParent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.mdview.ui.theme.LocalSkin
import com.mdview.ui.theme.MdViewMotion

/*
 * The two surfaces that float above the app, and the one treatment they share.
 *
 * Material draws both on its own `surfaceContainer` with tonal *and* shadow elevation,
 * which is a look this app decided against everywhere else: `DocumentCard` has used a
 * 1 dp hairline and no shadow since it was written, and `docs/SKINS.md` publishes that as
 * the rule -- "on a dark skin a shadow is invisible, which is exactly why the surface
 * ladder and the hairline exist". Menus and dialogs were simply the two places that had
 * never been brought into line.
 *
 * So, for both: `surfaceRaised`, a `border` hairline, `shape.large`, and zero elevation of
 * either kind.
 */

/**
 * Test handles for the dialog, whose panel and scrim carry no text of their own.
 *
 * The scrim especially needs one: it is an invisible full-screen target, and the only way
 * a test can prove tapping it dismisses the dialog.
 */
object OverlayTags {
    const val DIALOG = "overlay:dialog"
    const val DIALOG_SCRIM = "overlay:dialogScrim"
}

/**
 * A dropdown menu wearing the skin.
 *
 * A wrapper rather than five arguments repeated at each of the three call sites -- and
 * more importantly, one place to change when they should all change together.
 *
 * Its *transition* is still Material's. `DropdownMenu` resolves that through the
 * `MotionScheme` on `MaterialTheme`, which is `internal` in Material 3 1.4.0 and so cannot
 * be supplied from here; see [MdViewMotion]. The timing happens to land near
 * [MdViewMotion.Fast] already, which is why that constant is where it is.
 */
@Composable
fun SkinDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val skin = LocalSkin.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(skin.shape.large.dp),
        containerColor = skin.colors.surfaceRaised,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, skin.colors.border),
        content = content,
    )
}

/**
 * A confirmation dialog drawn and animated by this app rather than by the platform.
 *
 * Three things here are not what `AlertDialog` would have done, and each is deliberate:
 *
 * **It survives its own dismissal.** [visible] drives a [MutableTransitionState] and the
 * dialog stays composed while `currentState || targetState`, which is the only way to get
 * an *exit* animation -- returning early on `!visible` tears the window down mid-frame and
 * the panel simply vanishes.
 *
 * **The window itself does not animate.** The platform would fade the whole window in on
 * its own schedule, on top of ours, so it is zeroed for this window only. Doing it through
 * `android:windowAnimationStyle` in `themes.xml` would have caught the Activity too --
 * including the `recreate()` the language picker triggers.
 *
 * **The scrim is ours.** `usePlatformDefaultWidth = false` selects Compose's
 * `DialogWindowTheme`, which sets no `backgroundDimEnabled`, so the platform dim is gone
 * and the window fills the screen. That also means `dismissOnClickOutside` can never fire
 * again -- there is no longer an "outside" -- so the scrim carries the dismiss itself, and
 * `SkinOverlayTest` covers that because no compiler will.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkinDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    destructive: Boolean = false,
) {
    val panelState = remember { MutableTransitionState(false) }
    panelState.targetState = visible

    // Composed for one frame longer than it is wanted, on purpose: see the KDoc.
    if (!panelState.currentState && !panelState.targetState) return

    val skin = LocalSkin.current

    // One transition drives both the scrim and the panel. Giving each its own would mean
    // two Transitions writing `currentState` on the same MutableTransitionState, and the
    // first to finish would decide when the dialog leaves composition.
    val transition = rememberTransition(panelState, label = "dialog")
    // Deliberately kept as a State and read in the draw phase below, not unwrapped here:
    // reading it during composition would recompose this whole dialog on every frame of
    // the animation, and drag the window-animation SideEffect along with it.
    val scrimAlpha = transition.animateFloat(
        label = "scrimAlpha",
        transitionSpec = {
            if (targetState) {
                tween(MdViewMotion.Default, easing = MdViewMotion.Standard)
            } else {
                tween(MdViewMotion.Fast, easing = MdViewMotion.Exit)
            }
        },
    ) { shown -> if (shown) SCRIM_ALPHA else 0f }

    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxSize(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        SuppressWindowAnimation()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag(OverlayTags.DIALOG_SCRIM)
                // No indication: a ripple spreading across a full-screen scrim reads as a
                // rendering fault rather than as feedback.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                )
                .drawBehind { drawRect(color = Color.Black, alpha = scrimAlpha.value) },
            contentAlignment = Alignment.Center,
        ) {
            transition.AnimatedVisibility(
                visible = { shown -> shown },
                enter = MdViewMotion.dialogEnter,
                exit = MdViewMotion.dialogExit,
            ) {
                Column(
                    modifier = Modifier
                        .padding(28.dp)
                        .widthIn(max = PANEL_MAX_WIDTH)
                        .clip(RoundedCornerShape(skin.shape.large.dp))
                        .background(skin.colors.surfaceRaised)
                        .border(
                            1.dp,
                            skin.colors.border,
                            RoundedCornerShape(skin.shape.large.dp),
                        )
                        // Absorbs the tap so it cannot reach the scrim underneath. A
                        // no-op `clickable` would do it too, at the cost of announcing
                        // the whole panel as a button.
                        .pointerInput(Unit) { detectTapGestures { } }
                        .testTag(OverlayTags.DIALOG)
                        .padding(24.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = skin.colors.textPrimary,
                    )
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = skin.colors.textSecondary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextButton(onClick = onDismissRequest) {
                            Text(dismissLabel, color = skin.colors.textSecondary)
                        }
                        TextButton(onClick = onConfirm) {
                            Text(
                                text = confirmLabel,
                                color = if (destructive) {
                                    skin.colors.danger
                                } else {
                                    skin.colors.accent
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The message bar, rebuilt from tokens.
 *
 * Material's `Snackbar` has parameters for its shape and its colours but none for its
 * elevation, which is fixed at a 6 dp shadow -- the one thing that had to go. So the body
 * is drawn here instead, on the same raised-surface-and-hairline treatment as the menu and
 * the dialog.
 *
 * **Its enter and exit are still Material's**, and cannot be otherwise: `SnackbarHost`
 * hardcodes a fade-and-scale that reads no scheme. Replacing the host would also throw
 * away the accessibility-aware timeout it applies -- the reason a snackbar with an action
 * stays on screen longer for someone using a screen reader -- and 150 ms of easing is not
 * worth that trade. [MdViewMotion.Fast] is set nearby so the two read as one system.
 */
@Composable
fun SkinSnackbar(data: SnackbarData) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(skin.shape.medium.dp)

    Row(
        modifier = Modifier
            .padding(12.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(skin.colors.surfaceRaised)
            .border(1.dp, skin.colors.border, shape)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = data.visuals.message,
            style = MaterialTheme.typography.bodyMedium,
            color = skin.colors.textPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
        )
        data.visuals.actionLabel?.let { label ->
            TextButton(onClick = data::performAction) {
                Text(label, color = skin.colors.accent)
            }
        }
    }
}

/**
 * Turns off the platform's animation for the window this composable is drawn in.
 *
 * Walks the parent chain rather than casting `view.parent` directly: the dialog's content
 * view is not guaranteed to be an immediate child of the window's decor, and a hard cast
 * that is right today becomes a crash the first time Compose adds a layer.
 */
@Composable
private fun SuppressWindowAnimation() {
    val view = LocalView.current
    SideEffect {
        var parent: ViewParent? = view.parent
        while (parent != null && parent !is DialogWindowProvider) parent = parent.parent
        (parent as? DialogWindowProvider)?.window?.setWindowAnimations(0)
    }
}

/**
 * Dark enough to separate the panel from the document behind it, light enough that the
 * document is still legible as context. Deliberately not a skin token -- a scrim is the
 * absence of the interface, not part of it, and a skin that could tint it could also make
 * it invisible.
 */
private const val SCRIM_ALPHA = 0.45f

private val PANEL_MAX_WIDTH = 400.dp
