package com.pasich.encly.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically

/**
 * The app's motion tokens (Material 3 motion): the one small set of durations and easings every
 * animation draws from, so a checkbox, a folding row and a page all move in the same hand.
 *
 * Tweens, not springs, inside a screen: a spring's length depends on the distance it travels, so two
 * things that move together (a row folding while the keyboard rises) end at different times.
 */
object EnclyMotion {
    /** A small control changing state: a checkbox fill, a colour, a chevron (`short3`). */
    const val SHORT_MS = 150

    /** Content that changes size or place inside a screen: a row folding, a step sliding (`medium1`). */
    const val MEDIUM_MS = 250

    /** A screen or a large surface moving (`medium2`). The soft keyboard takes about as long. */
    const val LONG_MS = 300

    /**
     * Material's emphasized curve, for anything that stays on screen: it starts gently and settles slowly.
     * It is also the curve Android moves the soft keyboard on, so content that makes way for the keyboard
     * with it moves as one piece with it.
     */
    val Standard: Easing = CubicBezierEasing(a = 0.2f, b = 0f, c = 0f, d = 1f)

    /** Emphasized decelerate, for what comes in: it arrives fast and settles gently. */
    val Enter: Easing = CubicBezierEasing(a = 0.05f, b = 0.7f, c = 0.1f, d = 1f)

    /** Emphasized accelerate, for what leaves for good. */
    val Exit: Easing = CubicBezierEasing(a = 0.3f, b = 0f, c = 0.8f, d = 0.15f)

    /** [SHORT_MS] on [Standard]. */
    fun <T> short(): FiniteAnimationSpec<T> = tween(SHORT_MS, easing = Standard)

    /** [MEDIUM_MS] on [Standard]. */
    fun <T> medium(): FiniteAnimationSpec<T> = tween(MEDIUM_MS, easing = Standard)

    /** [LONG_MS] on [Standard]. */
    fun <T> long(): FiniteAnimationSpec<T> = tween(LONG_MS, easing = Standard)

    /** A block unfolding in a column: its height opens while it fades in. */
    val Unfold: EnterTransition get() = expandVertically(medium()) + fadeIn(medium())

    /** A block folding away in a column: it fades as its height closes. */
    val Fold: ExitTransition get() = shrinkVertically(medium()) + fadeOut(short())
}
