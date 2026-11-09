package com.pasich.encly.presentation.effects

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.pasich.encly.presentation.components.swipe.rememberReducedMotion

/**
 * The scope the navigation host's screens share elements in, provided once by AppNavHost. Null
 * outside it (previews, tests): nothing is shared there.
 */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The visibility scope of the destination being composed, provided by [animationScreens]. */
val LocalNavAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** Which end of the card-to-editor container transform the modifier goes on. */
enum class ContainerEnd { CARD, EDITOR }

/**
 * The container transform between a note's card and its editor: both carry a shared-bounds
 * modifier keyed by the note, so the card grows into the full-screen editor (and shrinks back),
 * its corner radius easing between the card's and 0 (see [cornerClip]) while the colours cross over.
 *
 * - The card re-measures to the animated bounds (`RemeasureToBounds`), so its surface fills the
 *   growing container instead of leaving a scaled card in the corner. The editor is scaled
 *   (`ScaleToBounds`) from its own full-size layout: re-measuring a whole text editor on every
 *   frame is wasteful, and its width is close to the card's, so the scale stays near 1.
 * - The editor is always drawn above the card in the overlay and is the one that fades; the card
 *   stays opaque beneath it, so no frame lets the screen behind show through a cross-fade.
 * - Nothing is shared (an empty modifier) without both scopes, with the system's "remove
 *   animations", or without a [noteId] (a new note has no card to grow from). When the other end
 *   is not composed (the card scrolled off, moved, archived or deleted from the editor), no match
 *   is found: the content stays in its place and the editor just fades.
 */
@Composable
fun rememberNoteContainerModifier(noteId: Long, end: ContainerEnd): Modifier {
    val sharedScope = LocalSharedTransitionScope.current
    val visibility = LocalNavAnimatedVisibilityScope.current
    val plain = rememberReducedMotion() || noteId <= 0L
    return if (sharedScope != null && visibility != null && !plain) {
        rememberContainerBounds(sharedScope, visibility, noteId, end)
    } else {
        Modifier
    }
}

@Composable
private fun rememberContainerBounds(
    sharedScope: SharedTransitionScope,
    visibility: AnimatedVisibilityScope,
    noteId: Long,
    end: ContainerEnd,
): Modifier {
    val corner = MaterialTheme.shapes.medium.topStart
    val windowSize = LocalWindowInfo.current.containerSize
    val clip = remember(corner, windowSize) { cornerClip(corner, windowSize) }
    val state = with(sharedScope) { rememberSharedContentState(key = "note-$noteId") }
    val bounds = with(sharedScope) {
        Modifier.sharedBounds(
            sharedContentState = state,
            animatedVisibilityScope = visibility,
            // No enter/exit here: fadeIn/fadeOut put the content in an offscreen layer for the whole
            // fade, and in the overlay that layer is a new surface for Skia to keep on every frame the
            // bounds change size. The editor's fade is drawn by [containerFade] without a layer.
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            boundsTransform = containerBounds(visibility, end),
            resizeMode = if (end == ContainerEnd.CARD) {
                SharedTransitionScope.ResizeMode.RemeasureToBounds
            } else {
                SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopStart)
            },
            zIndexInOverlay = if (end == ContainerEnd.EDITOR) 1f else 0f,
            clipInOverlayDuringTransition = clip,
        )
    }
    return if (end == ContainerEnd.EDITOR) bounds.then(containerFade(visibility)) else bounds
}

/**
 * The editor's fade as it enters and leaves, on the destination's own transition clock: its
 * `playTimeNanos` is read only when the layer is drawn (so a frame redraws the layer, nothing
 * recomposes), it follows a seek of the transition (the predictive back gesture), and it adds no
 * animation to the transition, so a leaving screen is never kept composed for it.
 *
 * The alpha is applied per draw call ([FADE_COMPOSITING]), not to the editor as one group: a group
 * alpha needs an offscreen surface, and while the card grows its bounds (and so the overlay's
 * layers) change size every frame. What differs from a group fade is only text over the editor's
 * own background, which is drawn at the same alpha beside it instead of inside it, for 120-220 ms
 * of a move that is already covered by the card's colour underneath.
 */
private fun containerFade(visibility: AnimatedVisibilityScope): Modifier = Modifier.graphicsLayer {
    val transition = visibility.transition
    alpha = containerFadeAlpha(
        current = transition.currentState,
        target = transition.targetState,
        playTimeMillis = transition.playTimeNanos / NANOS_PER_MILLI,
    )
    compositingStrategy = FADE_COMPOSITING
}

/** How the fade is composited: per draw call, never as an offscreen group (see [containerFade]). */
internal val FADE_COMPOSITING = CompositingStrategy.ModulateAlpha

private const val NANOS_PER_MILLI = 1_000_000f

/**
 * The editor's alpha at [playTimeMillis] into a change of the transition from [current] to [target].
 * Entering it waits [CONTAINER_FADE_IN_DELAY] ms and then eases in over [CONTAINER_FADE_IN] ms; leaving
 * it eases out over [CONTAINER_FADE_OUT] ms. At rest it is fully there or fully gone.
 */
internal fun containerFadeAlpha(current: EnterExitState, target: EnterExitState, playTimeMillis: Float): Float = when {
    current == target -> if (target == EnterExitState.Visible) 1f else 0f

    target == EnterExitState.Visible -> {
        val progress = ((playTimeMillis - CONTAINER_FADE_IN_DELAY) / CONTAINER_FADE_IN).coerceIn(0f, 1f)
        LinearOutSlowInEasing.transform(progress)
    }

    else -> {
        val progress = (playTimeMillis / CONTAINER_FADE_OUT).coerceIn(0f, 1f)
        1f - FastOutLinearInEasing.transform(progress)
    }
}

/**
 * The bounds of the card growing into the editor, and of the editor shrinking back (quicker). The
 * editor is on its way out when it is leaving; the card, when it is coming back.
 */
private fun containerBounds(visibility: AnimatedVisibilityScope, end: ContainerEnd) = BoundsTransform { _, _ ->
    val visible = visibility.transition.targetState == EnterExitState.Visible
    val closing = if (end == ContainerEnd.EDITOR) !visible else visible
    tween(if (closing) CONTAINER_CLOSE_DURATION else CONTAINER_DURATION, easing = ContainerEasing)
}

/** Below this share of the window a container still has the card's full corner radius. */
private const val ROUND_UNTIL_COVERAGE = 0.35f

/**
 * A rounded-rectangle clip of the animated bounds. The radius follows how much of the window the
 * bounds cover: the card's own while they are card-sized, easing to 0 as they fill the window.
 * It is computed here, while the content is lifted into the overlay, rather than by animating
 * a value on the destination's transition: any animation added to that transition would keep a
 * leaving screen composed until it ends, which a re-lock must never wait for.
 */
private fun cornerClip(corner: CornerSize, window: IntSize) = object : SharedTransitionScope.OverlayClip {
    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path {
        val windowArea = (window.width.toFloat() * window.height).coerceAtLeast(1f)
        val coverage = bounds.width * bounds.height / windowArea
        val roundness = 1f - ((coverage - ROUND_UNTIL_COVERAGE) / (1f - ROUND_UNTIL_COVERAGE)).coerceIn(0f, 1f)
        val radius = corner.toPx(bounds.size, density) * roundness
        return Path().apply { addRoundRect(RoundRect(bounds, CornerRadius(radius))) }
    }
}
