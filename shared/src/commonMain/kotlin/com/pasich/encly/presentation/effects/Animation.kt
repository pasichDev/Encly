package com.pasich.encly.presentation.effects

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset

private const val AXIS_DURATION = 300
private const val AXIS_FADE_IN = 210
private const val AXIS_FADE_IN_DELAY = 60
private const val AXIS_FADE_OUT = 120

/** How far a screen travels on the shared X axis: an eighth of the width. */
private const val AXIS_SHIFT_FRACTION = 8

/** Material's emphasized decelerate: screens arrive fast and settle gently. */
private val AxisEasing = CubicBezierEasing(a = 0.05f, b = 0.7f, c = 0.1f, d = 1f)

/**
 * Shared X axis (Material motion): going forward, the new screen slides in from the end side as it
 * fades in and the old one drifts toward the start as it fades out; [forward] false mirrors it for Back.
 * Start and end follow the layout direction, so a right-to-left layout moves the other way.
 *
 * The fade is not part of these transitions: `fadeIn`/`fadeOut` put a whole window of content in an
 * offscreen layer (about 10 MB at 1080x2400) for as long as they run. [screenFade] draws it instead,
 * per draw call, on the destination's own transition clock.
 */
fun AnimatedContentTransitionScope<*>.axisEnter(forward: Boolean): EnterTransition = slideIntoContainer(
    towards = if (forward) SlideDirection.Start else SlideDirection.End,
    animationSpec = tween(AXIS_DURATION, easing = AxisEasing),
    initialOffset = { fullSlide -> fullSlide / AXIS_SHIFT_FRACTION },
)

fun AnimatedContentTransitionScope<*>.axisExit(forward: Boolean): ExitTransition = slideOutOfContainer(
    towards = if (forward) SlideDirection.Start else SlideDirection.End,
    animationSpec = tween(AXIS_DURATION, easing = AxisEasing),
    targetOffset = { fullSlide -> fullSlide / AXIS_SHIFT_FRACTION },
)

/**
 * Duration of the card growing into the editor. A little longer than a screen slide: the card travels
 * and grows across the whole window, and at 300 ms that read as a jump rather than a growth.
 */
const val CONTAINER_DURATION = 340

/** Closing is quicker than opening: the user is leaving, and the shrink felt slow at the opening's pace. */
const val CONTAINER_CLOSE_DURATION = 220

/** The editor fades in over this part of the transform, starting [CONTAINER_FADE_IN_DELAY] in. */
const val CONTAINER_FADE_IN = 220

/** The card grows a little before the editor shows in it, so the motion leads and the content follows. */
const val CONTAINER_FADE_IN_DELAY = 60

/** Closing, the editor fades out quickly so the shrinking card is what the eye follows. */
const val CONTAINER_FADE_OUT = 120

/**
 * What keeps a screen composed for the length of a fade. A screen that only fades (the note list and
 * the editor) has no animation of its own left once the fade moved to [screenFade], and a transition
 * with none ends at once, taking the leaving screen with it. This moves it one pixel, an animation of
 * [durationMillis] that no layer and no eye can tell.
 */
fun holdEnter(durationMillis: Int): EnterTransition =
    slideIn(tween(durationMillis, easing = LinearEasing)) { IntOffset(-1, 0) }

fun holdExit(durationMillis: Int): ExitTransition =
    slideOut(tween(durationMillis, easing = LinearEasing)) { IntOffset(1, 0) }

/** How long a screen's fade runs, from [delayMillis] into its transition. */
class FadeTiming(val delayMillis: Int, val durationMillis: Int, val easing: Easing) {
    val endMillis: Int get() = delayMillis + durationMillis
}

private val AxisIn = FadeTiming(AXIS_FADE_IN_DELAY, AXIS_FADE_IN, FastOutSlowInEasing)
private val AxisOut = FadeTiming(0, AXIS_FADE_OUT, FastOutSlowInEasing)

/**
 * The cross-fade of the screens behind a container transform, paced with it: the screen being left
 * fades over the card's growth instead of blinking out under it, and the new one comes in a beat late.
 */
private val ContainerIn = FadeTiming(CONTAINER_FADE_IN_DELAY, CONTAINER_FADE_IN, LinearOutSlowInEasing)
private val ContainerOut = FadeTiming(0, CONTAINER_FADE_IN, FastOutLinearInEasing)

/** The fade a screen enters with under [motion], or null when it does not fade. */
internal fun ScreenMotion.enterFade(): FadeTiming? = when (this) {
    ScreenMotion.AXIS -> AxisIn
    ScreenMotion.FADE -> ContainerIn
    ScreenMotion.NONE -> null
}

/** The fade a screen leaves with under [motion], or null when it does not fade. */
internal fun ScreenMotion.exitFade(): FadeTiming? = when (this) {
    ScreenMotion.AXIS -> AxisOut
    ScreenMotion.FADE -> ContainerOut
    ScreenMotion.NONE -> null
}

/**
 * The alpha at [playTimeMillis] into a change of a transition from [current] to [target]: entering
 * waits [FadeTiming.delayMillis] and then eases in over [FadeTiming.durationMillis]; leaving eases out.
 * At rest it is fully there or fully gone. A null timing is no fade: fully there.
 */
internal fun fadeAlpha(
    enter: FadeTiming?,
    exit: FadeTiming?,
    current: EnterExitState,
    target: EnterExitState,
    playTimeMillis: Float,
): Float = when {
    current == target -> if (target == EnterExitState.Visible) 1f else 0f

    target == EnterExitState.Visible -> enter?.let {
        it.easing.transform(((playTimeMillis - it.delayMillis) / it.durationMillis).coerceIn(0f, 1f))
    } ?: 1f

    else -> exit?.let {
        1f - it.easing.transform(((playTimeMillis - it.delayMillis) / it.durationMillis).coerceIn(0f, 1f))
    } ?: 1f
}

/**
 * Fades the content with the destination's transition, with no `fadeIn`/`fadeOut` and so no offscreen
 * layer: the alpha is applied per draw call ([CompositingStrategy.ModulateAlpha]), read when the layer
 * is drawn (a frame redraws it, nothing recomposes), and follows a seek of the transition (the
 * predictive back gesture). What differs from a group fade is overlapping content showing through
 * itself for the length of the fade. The timings are read at draw time too, so a screen's motion can
 * be decided when its transition starts.
 */
fun Modifier.screenFade(
    visibility: AnimatedVisibilityScope,
    enter: () -> FadeTiming?,
    exit: () -> FadeTiming?,
): Modifier = graphicsLayer {
    val transition = visibility.transition
    alpha = fadeAlpha(
        enter = enter(),
        exit = exit(),
        current = transition.currentState,
        target = transition.targetState,
        playTimeMillis = transition.playTimeNanos / NANOS_PER_MILLI_F,
    )
    compositingStrategy = CompositingStrategy.ModulateAlpha
}

private const val NANOS_PER_MILLI_F = 1_000_000f

/**
 * The easing of the container transform's bounds and shape: Material's emphasized curve, which
 * starts gently and settles slowly, unlike the axis curve's sharp start.
 */
val ContainerEasing = CubicBezierEasing(a = 0.2f, b = 0f, c = 0f, d = 1f)
