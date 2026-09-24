package com.pasich.encly.presentation.screen

import com.pasich.encly.R
import com.pasich.encly.presentation.viewmodel.PinUnlockResult
import com.pasich.encly.presentation.viewmodel.SeedUnlockResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockFormStateTest {
    private val form = LockFormState()

    private fun typePin(pin: String) = pin.forEach { form.typeDigit(it.digitToInt()) }

    @Test
    fun aWrongPinBeforeTheLockoutShowsTheErrorAndShakes() {
        typePin("123456")
        assertEquals("123456", form.takePin())

        form.onPinResult(PinUnlockResult.WRONG_PIN, lockoutRemainingMillis = 0L)

        assertEquals(R.string.lock_wrong_pin, form.pinError)
        assertEquals(1, form.shakeKey)
        assertEquals("", form.pin)
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
        assertEquals("", form.pin)
    }

    @Test
    fun aWrongPhraseKeepsThePhraseAndShowsTheError() {
        form.editPhrase("orbit cactus velvet")

        form.onSeedResult(SeedUnlockResult.WRONG_SEED)

        assertEquals("orbit cactus velvet", form.phrase)
        assertEquals(R.string.lock_wrong_recovery_phrase, form.phraseError)
        form.editPhrase("orbit cactus velvets")
        assertNull(form.phraseError)
    }

    @Test
    fun aBackgroundedUnlockShowsNoError() {
        form.onPinResult(PinUnlockResult.BACKGROUNDED, lockoutRemainingMillis = 0L)
        form.onSeedResult(SeedUnlockResult.BACKGROUNDED)

        assertNull(form.pinError)
        assertNull(form.phraseError)
        assertTrue(form.pin.isEmpty())
    }
}
