package com.pasich.encly.presentation.screen.settings

import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.key_store_unavailable
import com.pasich.encly.core.resources.keyring_not_running
import com.pasich.encly.core.resources.pin_current_wrong
import com.pasich.encly.core.security.KeyStoreProblem
import com.pasich.encly.core.security.PinCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** The check of the current secret in a settings flow: what it says, and that a lock in the middle ends it. */
class PinFlowLockTest {
    private var answer: ((PinCheck) -> Unit)? = null
    private var problem: KeyStoreProblem? = null
    private val actions = PinFlowActions(
        verify = { _, callback -> answer = callback },
        lockoutMillis = { 0L },
        save = { },
        close = { },
        keyStoreProblem = { problem },
    )

    private fun checking(): PinFlowState {
        val state = PinFlowState()
        repeat(6) { state.typeDigit(it + 1) }
        state.onPinComplete(actions)
        return state
    }

    @Test
    fun aRightSecretMovesOnAndAWrongOneSaysWrong() {
        val state = checking()
        answer!!(PinCheck.OK)
        assertEquals(PinStep.OPTIONS, state.step)

        val wrong = checking()
        answer!!(PinCheck.WRONG)
        assertEquals(Res.string.pin_current_wrong, wrong.errorText)
        assertEquals(PinStep.VERIFY, wrong.step)
    }

    @Test
    fun aKeyStoreThatDidNotAnswerSaysWhatItIsMissingAndNotWrong() {
        problem = KeyStoreProblem.NOT_RUNNING
        val state = checking()
        answer!!(PinCheck.KEY_STORE_UNAVAILABLE)
        assertEquals(Res.string.keyring_not_running, state.errorText)

        problem = KeyStoreProblem.LOCKED
        val locked = checking()
        answer!!(PinCheck.KEY_STORE_UNAVAILABLE)
        assertEquals(Res.string.key_store_unavailable, locked.errorText)
    }

    @Test
    fun aCheckThatIsAnsweredAfterTheVaultLockedDoesNotMoveTheFlowOn() {
        val state = checking()
        assertEquals(true, state.busy)

        state.lock()
        answer!!(PinCheck.OK)

        assertEquals(PinStep.VERIFY, state.step)
        assertFalse(state.busy)
        assertNull(state.errorText)
    }
}
