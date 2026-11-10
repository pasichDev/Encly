package com.pasich.encly.presentation.effects

import androidx.compose.animation.EnterExitState
import androidx.compose.ui.graphics.CompositingStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The editor's fade in the container transform. Whether a `graphicsLayer` block really allocates no
 * offscreen surface cannot be read back from a node, so the strategy it is given is a named constant
 * that is asserted here, and the alpha itself is a pure function of the transition's clock.
 */
class ContainerFadeTest {
    private val visible = EnterExitState.Visible
    private val preEnter = EnterExitState.PreEnter
    private val postExit = EnterExitState.PostExit

    @Test
    fun theFadeIsNeverCompositedAsAnOffscreenGroup() {
        assertEquals(CompositingStrategy.ModulateAlpha, FADE_COMPOSITING)
        assertNotEquals(CompositingStrategy.Auto, FADE_COMPOSITING)
        assertNotEquals(CompositingStrategy.Offscreen, FADE_COMPOSITING)
    }

    @Test
    fun atRestTheEditorIsThereOrGone() {
        assertEquals(1f, containerFadeAlpha(visible, visible, 0f))
        assertEquals(1f, containerFadeAlpha(visible, visible, 9_999f))
        assertEquals(0f, containerFadeAlpha(postExit, postExit, 0f))
        assertEquals(0f, containerFadeAlpha(preEnter, preEnter, 0f))
    }

    @Test
    fun enteringWaitsThenEasesInAndEndsOpaque() {
        assertEquals(0f, containerFadeAlpha(preEnter, visible, 0f))
        assertEquals(0f, containerFadeAlpha(preEnter, visible, CONTAINER_FADE_IN_DELAY.toFloat()))
        val midway = containerFadeAlpha(preEnter, visible, (CONTAINER_FADE_IN_DELAY + CONTAINER_FADE_IN / 2).toFloat())
        assertTrue(midway > 0f && midway < 1f, "midway was $midway")
        assertEquals(1f, containerFadeAlpha(preEnter, visible, (CONTAINER_FADE_IN_DELAY + CONTAINER_FADE_IN).toFloat()))
        assertEquals(1f, containerFadeAlpha(preEnter, visible, 10_000f))
    }

    @Test
    fun leavingEasesOutQuicklyAndEndsGone() {
        assertEquals(1f, containerFadeAlpha(visible, postExit, 0f))
        val midway = containerFadeAlpha(visible, postExit, CONTAINER_FADE_OUT / 2f)
        assertTrue(midway > 0f && midway < 1f, "midway was $midway")
        assertEquals(0f, containerFadeAlpha(visible, postExit, CONTAINER_FADE_OUT.toFloat()))
        assertEquals(0f, containerFadeAlpha(visible, postExit, 10_000f))
    }

    @Test
    fun theAlphaNeverLeavesTheUnitRange() {
        for (t in -50..1_000 step 7) {
            for ((from, to) in listOf(preEnter to visible, visible to postExit, visible to visible)) {
                val alpha = containerFadeAlpha(from, to, t.toFloat())
                assertTrue(alpha in 0f..1f, "alpha $alpha at $t ms ($from to $to)")
            }
        }
    }
}
