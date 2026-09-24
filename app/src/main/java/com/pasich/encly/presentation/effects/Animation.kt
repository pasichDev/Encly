package com.pasich.encly.presentation.effects

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically

private const val DEFAULT_FADE_DURATION = 300
private const val DEFAULT_SCALE_DURATION = 400
private const val DEFAULT_INITIAL_SCALE = 0.9f

/** The first screen after unlock rises in while [UnlockRevealOverlay] washes to its ground. */
private const val UNLOCK_ENTER_DELAY = 380
private const val UNLOCK_ENTER_DURATION = 280
private const val UNLOCK_RISE_FRACTION = 60

fun defaultScreenEnterAnimation(): EnterTransition = fadeIn(animationSpec = tween(DEFAULT_FADE_DURATION)) +
    scaleIn(
        initialScale = DEFAULT_INITIAL_SCALE,
        animationSpec = tween(DEFAULT_SCALE_DURATION),
    )

fun defaultScreenExitAnimation(): ExitTransition = fadeOut(animationSpec = tween(DEFAULT_FADE_DURATION)) +
    scaleOut(
        targetScale = DEFAULT_INITIAL_SCALE,
        animationSpec = tween(DEFAULT_SCALE_DURATION),
    )

fun unlockScreenEnterAnimation(): EnterTransition =
    fadeIn(animationSpec = tween(UNLOCK_ENTER_DURATION, delayMillis = UNLOCK_ENTER_DELAY)) +
        slideInVertically(animationSpec = tween(UNLOCK_ENTER_DURATION, delayMillis = UNLOCK_ENTER_DELAY)) {
            it / UNLOCK_RISE_FRACTION
        }
