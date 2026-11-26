package com.pasich.encly.presentation.screen

import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.lock_pin_key_lost
import com.pasich.encly.core.resources.lock_wrong_pin
import com.pasich.encly.presentation.viewmodel.PinUnlockResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lock screen's state where the secret is a passphrase: typed in a field, taken out when it is sent. */
class LockFormPassphraseTest {
    private val form = LockFormState()

    @Test
    fun theUnlockButtonIsOnAsSoonAsSomethingIsTyped() {
        assertFalse(form.canSubmitPassphrase)

        form.passphrase.edit("x")

        assertTrue(form.canSubmitPassphrase)
    }

    @Test
    fun takingThePassphraseEmptiesTheField() {
        form.passphrase.edit("correct horse battery")

        assertEquals("correct horse battery", String(form.takePassphrase()))

        assertEquals("", form.passphrase.text)
        assertFalse(form.canSubmitPassphrase)
    }

    @Test
    fun aWrongAttemptShowsTheOneErrorAndLeavesTheFieldEmpty() {
        form.passphrase.edit("correct horse battery")
        form.takePassphrase()

        form.onPinResult(PinUnlockResult.WRONG_PIN, lockoutRemainingMillis = 0L)

        // The same resource whichever secret was typed: the screen words it for the kind.
        assertEquals(Res.string.lock_wrong_pin, form.pinError)
        assertEquals(1, form.shakeKey)
        assertEquals("", form.passphrase.text)
    }

    @Test
    fun editingTheNextAttemptClearsTheError() {
        form.onPinResult(PinUnlockResult.WRONG_PIN, lockoutRemainingMillis = 0L)

        form.onPassphraseEdited()

        assertNull(form.pinError)
    }

    @Test
    fun editingWithoutAnErrorChangesNothing() {
        form.onPassphraseEdited()

        assertNull(form.pinError)
    }

    @Test
    fun aLostKeyAsksForTheRecoveryPhraseWithTheSameResourceAsAPin() {
        form.onPinResult(PinUnlockResult.KEY_LOST, lockoutRemainingMillis = 0L)

        assertEquals(Res.string.lock_pin_key_lost, form.pinError)
        assertEquals(Res.string.lock_pin_key_lost, form.phraseError)
    }

    @Test
    fun aLockoutStartsTheCountdown() {
        form.onPinResult(PinUnlockResult.WRONG_PIN, lockoutRemainingMillis = 30_000L)

        assertEquals(30L, form.lockoutSeconds)
        assertTrue(form.lockedOut)
    }

    @Test
    fun leavingTheScreenForgetsWhatWasTypedAndDigitsToo() {
        form.passphrase.edit("never sent")
        form.passphrase.revealed = true
        form.typeDigit(1)

        form.clearPin()

        assertEquals("", form.passphrase.text)
        assertFalse(form.passphrase.revealed)
        assertEquals(0, form.pinLength)
    }
}
