package com.pasich.encly.presentation.effects

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

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
 */
fun AnimatedContentTransitionScope<*>.axisEnter(forward: Boolean): EnterTransition = slideIntoContainer(
    towards = if (forward) SlideDirection.Start else SlideDirection.End,
    animationSpec = tween(AXIS_DURATION, easing = AxisEasing),
    initialOffset = { fullSlide -> fullSlide / AXIS_SHIFT_FRACTION },
) + fadeIn(tween(AXIS_FADE_IN, delayMillis = AXIS_FADE_IN_DELAY))

fun AnimatedContentTransitionScope<*>.axisExit(forward: Boolean): ExitTransition = slideOutOfContainer(
    towards = if (forward) SlideDirection.Start else SlideDirection.End,
    animationSpec = tween(AXIS_DURATION, easing = AxisEasing),
    targetOffset = { fullSlide -> fullSlide / AXIS_SHIFT_FRACTION },
) + fadeOut(tween(AXIS_FADE_OUT))

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
 * The cross-fade of the screens behind a container transform, paced with it: the screen being left
 * fades over the card's growth instead of blinking out under it, and the new one comes in a beat late.
 */
fun fadeScreenEnter(): EnterTransition =
    fadeIn(tween(CONTAINER_FADE_IN, delayMillis = CONTAINER_FADE_IN_DELAY, easing = LinearOutSlowInEasing))

fun fadeScreenExit(): ExitTransition = fadeOut(tween(CONTAINER_FADE_IN, easing = FastOutLinearInEasing))

/**
 * The easing of the container transform's bounds and shape: Material's emphasized curve, which
 * starts gently and settles slowly, unlike the axis curve's sharp start.
 */
val ContainerEasing = CubicBezierEasing(a = 0.2f, b = 0f, c = 0f, d = 1f)
