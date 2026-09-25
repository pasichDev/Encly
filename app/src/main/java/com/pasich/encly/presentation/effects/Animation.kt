package com.pasich.encly.presentation.effects

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

private const val AXIS_DURATION = 300
private const val AXIS_FADE_IN = 210
private const val AXIS_FADE_IN_DELAY = 60
private const val AXIS_FADE_OUT = 120

/** How far a screen travels on the shared X axis: an eighth of the width. */
private const val AXIS_SHIFT_FRACTION = 8

/** Material's emphasized decelerate: screens arrive fast and settle gently. */
private val AxisEasing = CubicBezierEasing(a = 0.05f, b = 0.7f, c = 0.1f, d = 1f)

/**
 * Shared X axis (Material motion): going forward, the new screen slides in from the right as it
 * fades in and the old one drifts left as it fades out; [forward] false mirrors it for Back.
 */
fun axisEnter(forward: Boolean): EnterTransition =
    slideInHorizontally(tween(AXIS_DURATION, easing = AxisEasing)) { width ->
        (if (forward) width else -width) / AXIS_SHIFT_FRACTION
    } + fadeIn(tween(AXIS_FADE_IN, delayMillis = AXIS_FADE_IN_DELAY))

fun axisExit(forward: Boolean): ExitTransition =
    slideOutHorizontally(tween(AXIS_DURATION, easing = AxisEasing)) { width ->
        (if (forward) -width else width) / AXIS_SHIFT_FRACTION
    } + fadeOut(tween(AXIS_FADE_OUT))
