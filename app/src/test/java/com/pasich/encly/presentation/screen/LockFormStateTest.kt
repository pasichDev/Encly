package com.pasich.encly.presentation.screen

import com.pasich.encly.R
import com.pasich.encly.presentation.viewmodel.PinUnlockResult
import com.pasich.encly.presentation.viewmodel.SeedUnlockResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockFormStateTest {
    private val form = LockFormState()

    private fun typePin(pin: String) = pin.forEach { form.typeDigit(it.digitToInt()) }

    @Test
    fun aWrongPinBeforeTheLockoutShowsTheErrorAndShakes() {
        typePin("123456")
        assertEquals("123456", String(form.takePin()))

        form.onPinResult(PinUnlockResult.WRONG_PIN, lockoutRemainingMillis = 0L)

        assertEquals(R.string.lock_wrong_pin, form.pinError)
        assertEquals(1, form.shakeKey)
        assertEquals(0, form.pinLength)
        assertEquals(0L, form.lockoutSeconds)
    }

    @Test
    fun theNextAttemptClearsTheError() {
        form.onPinResult(PinUnlockResult.WRONG_PIN, lockoutRemainingMillis = 0L)

        form.typeDigit(1)

        assertNull(form.pinError)
    }

    @Test
    fun aWrongPinThatStartsALockoutCountsItDownAndBlocksDigits() {
        form.onPinResult(PinUnlockResult.WRONG_PIN, lockoutRemainingMillis = 30_000L)

        assertEquals(30L, form.lockoutSeconds)
        form.typeDigit(1)
        assertEquals(0, form.pinLength)
    }

    @Test
    fun aWrongPhraseKeepsTheWordsAndShowsTheError() {
        form.phrase.onValueChange(0, "orbit cactus velvet")

        form.onSeedResult(SeedUnlockResult.WRONG_SEED)

        assertEquals(listOf("orbit", "cactus", "velvet"), form.phrase.words.take(3))
        assertEquals(R.string.lock_wrong_recovery_phrase, form.phraseError)
        form.onPhraseEdited()
        assertNull(form.phraseError)
    }

    @Test
    fun backToThePinPadDropsTheWords() {
        form.useRecovery = true
        form.phrase.onValueChange(0, "orbit cactus")
        form.onSeedResult(SeedUnlockResult.WRONG_SEED)

        form.leaveRecovery()

        assertFalse(form.useRecovery)
        assertTrue(form.phrase.words.all(String::isEmpty))
        assertNull(form.phraseError)
    }

    @Test
    fun aBackgroundedUnlockShowsNoError() {
        form.onPinResult(PinUnlockResult.BACKGROUNDED, lockoutRemainingMillis = 0L)
        form.onSeedResult(SeedUnlockResult.BACKGROUNDED)

        assertNull(form.pinError)
        assertNull(form.phraseError)
        assertEquals(0, form.pinLength)
    }

    @Test
    fun theTypedDigitsAreWipedWhenTakenOrCleared() {
        typePin("12")
        form.deleteDigit()
        typePin("3456")
        assertEquals(5, form.pinLength)
        form.typeDigit(7)

        val taken = form.takePin()

        assertEquals("134567", String(taken))
        assertEquals(0, form.pinLength)
        typePin("99")
        form.clearPin()
        assertEquals(0, form.pinLength)
        assertEquals("a new PIN starts from nothing", "1", String(form.also { it.typeDigit(1) }.takePin()))
    }

    @Test
    fun aLostPinKeyExplainsItOnBothForms() {
        form.onPinResult(PinUnlockResult.KEY_LOST, lockoutRemainingMillis = 0L)

        assertEquals(R.string.lock_pin_key_lost, form.pinError)
        assertEquals(R.string.lock_pin_key_lost, form.phraseError)
    }
}
