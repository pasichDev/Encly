package com.pasich.encly.presentation.effects

import android.provider.Settings
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Rect
import com.pasich.encly.ui.screens.ComposeScreenTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The unlock reveal: when the lock screen is left, how often the full animation plays, and how it is cut short. */
class UnlockRevealTest : ComposeScreenTest() {
    private val state = UnlockRevealState()
    private var covered = 0

    private val logo = Rect(left = 100f, top = 200f, right = 164f, bottom = 264f)

    @Test
    fun withoutTheLogoBoundsTheLockScreenIsLeftAtOnce() {
        state.start { covered++ }

        assertEquals(1, covered)
        assertFalse(state.running)
    }

    @Test
    fun withTheLogoTheLockScreenIsLeftOnlyOnceCovered() {
        state.logoBounds = logo

        state.start { covered++ }

        assertTrue(state.running)
        assertEquals("nothing happens until the colour fills the window", 0, covered)

        state.cover()
        state.cover()
        assertEquals("leaving happens once", 1, covered)
        assertEquals("the screens wait off the right edge", 1f, state.contentOffset, 0f)
    }

    @Test
    fun theListSlidesInAsTheProgressGrowsAndFinishingSettlesIt() {
        state.logoBounds = logo
        state.start {}
        state.cover()

        state.push(0.25f)
        assertEquals(0.75f, state.contentOffset, 0.0001f)
        state.push(1f)
        assertEquals(0f, state.contentOffset, 0f)

        state.finish()
        assertFalse(state.running)
        assertEquals(0f, state.contentOffset, 0f)
    }

    @Test
    fun aSecondStartWhilePlayingChangesNothing() {
        state.logoBounds = logo
        state.start { covered += 1 }

        state.start { covered += 100 }
        state.cover()

        assertEquals(1, covered)
    }

    @Test
    fun onlyTheFirstUnlockGetsTheFullReveal() {
        state.logoBounds = logo
        state.start {}
        assertTrue(state.full)
        state.finish()

        state.start {}

        assertFalse(state.full)
    }

    @Test
    fun cancellingDropsTheLeaveAndPutsTheScreensBack() {
        state.logoBounds = logo
        state.start { covered++ }
        state.cover()

        state.cancel()

        assertFalse(state.running)
        assertEquals(0f, state.contentOffset, 0f)
        state.cover()
        assertEquals("the pending navigation does not run after a cancel", 1, covered)
    }

    @Test
    fun revealThenRunsTheLeaveDirectlyWithoutAReveal() {
        var left = 0
        val none: UnlockRevealState? = null

        none.revealThen { left++ }

        assertEquals(1, left)
    }

    @Test
    fun revealThenStartsTheRevealWhenOneIsProvided() {
        var left = 0
        state.logoBounds = logo

        state.revealThen { left++ }

        assertTrue(state.running)
        assertEquals(0, left)
    }

    @Test
    fun theFullRevealPlaysToTheEndAndLeavesTheLockScreenOnce() {
        state.logoBounds = logo
        setScreen { UnlockRevealOverlay(state) }

        rule.runOnIdle { state.start { covered++ } }
        waitFor { !state.running }

        assertEquals(1, covered)
        assertEquals(0f, state.contentOffset, 0f)
    }

    @Test
    fun theLaterRevealIsTheShortFadeAndAlsoLeavesOnce() {
        state.logoBounds = logo
        setScreen { UnlockRevealOverlay(state) }
        rule.runOnIdle { state.start { covered++ } }
        waitFor { !state.running }

        rule.runOnIdle { state.start { covered++ } }
        assertFalse(state.full)
        waitFor { !state.running }

        assertEquals(2, covered)
        assertEquals(0f, state.contentOffset, 0f)
    }

    @Test
    fun aRevealCancelledMidwayNeverLeavesTheLockScreen() {
        state.logoBounds = logo
        setScreen { UnlockRevealOverlay(state) }
        rule.mainClock.autoAdvance = false

        rule.runOnIdle { state.start { covered++ } }
        rule.mainClock.advanceTimeBy(50)
        rule.runOnIdle { state.cancel() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertEquals(0, covered)
        assertFalse(state.running)
    }

    // One frame of the test clock; the budgets below are what a user waits behind the colour.
    private val frameMs = 16L

    /** Starts the reveal on the paused clock and gives the overlay its first frames to compose and launch. */
    private fun startAndLetItBegin() {
        rule.runOnIdle { state.start { covered++ } }
        // The paused clock does not idle the looper that delivers the state change to the composition.
        Snapshot.sendApplyNotifications()
        repeat(3) { rule.mainClock.advanceTimeByFrame() }
    }

    @Test
    fun theFullRevealIsOverWithinHalfASecond() {
        state.logoBounds = logo
        setScreen { UnlockRevealOverlay(state) }
        rule.mainClock.autoAdvance = false

        startAndLetItBegin()
        rule.mainClock.advanceTimeBy(UNLOCK_GROW_MS + frameMs * (SETTLE_FRAMES + 2))
        assertEquals("the list is out of sight once the colour has grown", 1, covered)
        assertTrue("the push is still playing", state.running)

        rule.mainClock.advanceTimeBy(UNLOCK_PUSH_MS + frameMs)
        assertFalse(state.running)
        assertTrue(UNLOCK_GROW_MS + SETTLE_FRAMES * frameMs + UNLOCK_PUSH_MS <= 500)
    }

    @Test
    fun theLaterRevealIsShorterStill() {
        state.logoBounds = logo
        setScreen { UnlockRevealOverlay(state) }
        rule.runOnIdle { state.start {} }
        waitFor { !state.running }
        rule.mainClock.autoAdvance = false

        startAndLetItBegin()
        rule.mainClock.advanceTimeBy(QUICK_OUT_MS + QUICK_IN_MS + frameMs * (SETTLE_FRAMES + 3))

        assertEquals(1, covered)
        assertFalse(state.running)
    }

    @Test
    fun withAnimationsOffTheLockScreenIsLeftAtOnceWithoutAnyColour() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        state.logoBounds = logo
        setScreen { UnlockRevealOverlay(state) }
        rule.mainClock.autoAdvance = false

        startAndLetItBegin()
        rule.mainClock.advanceTimeBy(frameMs * 2)

        assertEquals(1, covered)
        assertFalse(state.running)
        assertEquals(0f, state.contentOffset, 0f)
    }
}
