package com.pasich.encly.presentation.effects

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.graphics.Path
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
    return with(sharedScope) {
        Modifier.sharedBounds(
            sharedContentState = state,
            animatedVisibilityScope = visibility,
            enter = if (end == ContainerEnd.EDITOR) fadeIn(ContainerFadeIn) else EnterTransition.None,
            exit = if (end == ContainerEnd.EDITOR) fadeOut(ContainerFadeOut) else ExitTransition.None,
            boundsTransform = ContainerBounds,
            resizeMode = if (end == ContainerEnd.CARD) {
                SharedTransitionScope.ResizeMode.RemeasureToBounds
            } else {
                SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopStart)
            },
            zIndexInOverlay = if (end == ContainerEnd.EDITOR) 1f else 0f,
            clipInOverlayDuringTransition = clip,
        )
    }
}

private val ContainerFadeIn =
    tween<Float>(CONTAINER_FADE_IN, delayMillis = CONTAINER_FADE_IN_DELAY, easing = LinearOutSlowInEasing)

private val ContainerFadeOut = tween<Float>(CONTAINER_FADE_OUT, easing = FastOutLinearInEasing)

private val ContainerBounds = BoundsTransform { _, _ -> tween(CONTAINER_DURATION, easing = ContainerEasing) }

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
