package com.pasich.encly.presentation.effects

import androidx.compose.animation.EnterExitState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The fade of a screen swap, now drawn from the transition's clock instead of by `fadeIn`/`fadeOut`
 * (which put the whole window in an offscreen layer). It keeps the timings those had, and a screen
 * that must not fade (the lock screen) does not.
 */
class ScreenFadeTest {
    private val visible = EnterExitState.Visible
    private val preEnter = EnterExitState.PreEnter
    private val postExit = EnterExitState.PostExit

    private fun alpha(motion: ScreenMotion, from: EnterExitState, to: EnterExitState, ms: Float) =
        fadeAlpha(motion.enterFade(), motion.exitFade(), from, to, ms)

    @Test
    fun theSharedAxisKeepsItsOldTimings() {
        val enter = assertNotNull(ScreenMotion.AXIS.enterFade())
        val exit = assertNotNull(ScreenMotion.AXIS.exitFade())
        assertEquals(60, enter.delayMillis)
        assertEquals(210, enter.durationMillis)
        assertEquals(0, exit.delayMillis)
        assertEquals(120, exit.durationMillis)
    }

    @Test
    fun theContainerFadeKeepsItsOldTimings() {
        val enter = assertNotNull(ScreenMotion.FADE.enterFade())
        val exit = assertNotNull(ScreenMotion.FADE.exitFade())
        assertEquals(CONTAINER_FADE_IN_DELAY, enter.delayMillis)
        assertEquals(CONTAINER_FADE_IN, enter.durationMillis)
        assertEquals(CONTAINER_FADE_IN, exit.durationMillis)
    }

    @Test
    fun noMotionNeverFades() {
        assertNull(ScreenMotion.NONE.enterFade())
        assertNull(ScreenMotion.NONE.exitFade())
        for (t in 0..400 step 20) {
            assertEquals(1f, alpha(ScreenMotion.NONE, preEnter, visible, t.toFloat()))
            assertEquals(1f, alpha(ScreenMotion.NONE, visible, postExit, t.toFloat()))
        }
    }

    @Test
    fun atRestAScreenIsThereOrGone() {
        for (motion in ScreenMotion.entries) {
            assertEquals(1f, alpha(motion, visible, visible, 0f))
            assertEquals(0f, alpha(motion, postExit, postExit, 0f))
            assertEquals(0f, alpha(motion, preEnter, preEnter, 0f))
        }
    }

    @Test
    fun enteringWaitsThenEndsOpaqueAndLeavingEndsGone() {
        for (motion in listOf(ScreenMotion.AXIS, ScreenMotion.FADE)) {
            val enter = assertNotNull(motion.enterFade())
            val exit = assertNotNull(motion.exitFade())
            assertEquals(0f, alpha(motion, preEnter, visible, 0f))
            assertEquals(1f, alpha(motion, preEnter, visible, enter.endMillis.toFloat()))
            assertEquals(1f, alpha(motion, visible, postExit, 0f))
            assertEquals(0f, alpha(motion, visible, postExit, exit.endMillis.toFloat()))
        }
    }

    @Test
    fun theAlphaNeverLeavesTheUnitRange() {
        for (motion in ScreenMotion.entries) {
            for (t in -50..1_000 step 7) {
                for ((from, to) in listOf(preEnter to visible, visible to postExit)) {
                    val a = alpha(motion, from, to, t.toFloat())
                    assertTrue(a in 0f..1f, "alpha $a at $t ms ($motion, $from to $to)")
                }
            }
        }
    }

    @Test
    fun aFadingScreenIsHeldForAsLongAsItsFade() {
        // Its motion is only a one-pixel hold, so the transition must last as long as the fade.
        assertEquals(CONTAINER_FADE_IN_DELAY + CONTAINER_FADE_IN, ScreenMotion.FADE.enterFade()?.endMillis)
        assertEquals(CONTAINER_FADE_IN, ScreenMotion.FADE.exitFade()?.endMillis)
    }

    @Test
    fun aScreenWithNoRecordedMotionDoesNotFade() {
        assertEquals(ScreenMotion.NONE, ScreenMotions.of("never-seen"))
        ScreenMotions.record("e1", ScreenMotion.AXIS)
        assertEquals(ScreenMotion.AXIS, ScreenMotions.of("e1"))
        ScreenMotions.record("e1", ScreenMotion.FADE)
        assertEquals(ScreenMotion.FADE, ScreenMotions.of("e1"))
    }

    @Test
    fun theRegistryKeepsOnlyTheLatestEntries() {
        repeat(100) { ScreenMotions.record("old-$it", ScreenMotion.AXIS) }
        assertEquals(ScreenMotion.AXIS, ScreenMotions.of("old-99"))
        assertEquals(ScreenMotion.NONE, ScreenMotions.of("old-0"))
    }
}
