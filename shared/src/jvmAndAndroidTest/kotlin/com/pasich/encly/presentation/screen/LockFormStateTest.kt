package com.pasich.encly.presentation.screen

import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.lock_pin_key_lost
import com.pasich.encly.core.resources.lock_wrong_password
import com.pasich.encly.core.resources.lock_wrong_pin
import com.pasich.encly.core.resources.lock_wrong_recovery_phrase
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

        assertEquals(Res.string.lock_wrong_pin, form.pinError)
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
        assertEquals(Res.string.lock_wrong_recovery_phrase, form.phraseError)
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

        assertEquals(Res.string.lock_pin_key_lost, form.pinError)
        assertEquals(Res.string.lock_pin_key_lost, form.phraseError)
    }

    @Test
    fun thePinTakesSixToTwelveDigitsAndIgnoresTheRest() {
        typePin("12345")
        assertFalse("five digits are no PIN", form.canSubmitPin)
        typePin("6")
        assertTrue(form.canSubmitPin)

        typePin("78901234567")

        assertEquals(12, form.pinLength)
        assertEquals("123456789012", String(form.takePin()))
    }

    @Test
    fun theDotsKeepTheLengthOfThePinThatWasSentForChecking() {
        typePin("12345678")

        form.takePin()

        assertEquals(0, form.pinLength)
        assertEquals(8, form.submittedLength)
    }

    @Test
    fun aWrongPasswordShowsItsErrorAndKeepsThePinPadClean() {
        form.usePassword = true
        form.password.text = "not the password"

        form.onPasswordResult(PinUnlockResult.WRONG_PIN, lockoutRemainingMillis = 0L)

        assertEquals(Res.string.lock_wrong_password, form.passwordError)
        assertNull(form.pinError)
        form.onPasswordEdited()
        assertNull(form.passwordError)
    }

    @Test
    fun aPasswordFailureStartsTheSameLockoutCountdown() {
        form.onPasswordResult(PinUnlockResult.WRONG_PIN, lockoutRemainingMillis = 30_000L)

        assertEquals(30L, form.lockoutSeconds)
        assertTrue(form.lockedOut)
    }

    @Test
    fun aLostKeyExplainsItOnThePasswordFormAndTheRecoveryForm() {
        form.onPasswordResult(PinUnlockResult.KEY_LOST, lockoutRemainingMillis = 0L)

        assertEquals(Res.string.lock_pin_key_lost, form.passwordError)
        assertEquals(Res.string.lock_pin_key_lost, form.phraseError)
    }

    @Test
    fun switchingMethodsDropsWhatWasTyped() {
        typePin("123456")
        form.switchTo(usePassword = true)

        assertTrue(form.usePassword)
        assertEquals(0, form.pinLength)

        form.password.text = "correct horse"
        form.switchTo(usePassword = false)

        assertFalse(form.usePassword)
        assertEquals("", form.password.text)
    }

    @Test
    fun thePasswordIsTakenOutOfTheFormToBeChecked() {
        form.password.text = "correct horse"
        assertTrue(form.password.canSubmit)

        val taken = form.takePassword()

        assertEquals("correct horse", String(taken))
        assertEquals("", form.password.text)
        assertFalse(form.password.canSubmit)
    }

    @Test
    fun aPasswordNeedsEightCharactersCountedAsCodePoints() {
        form.password.text = "1234567"
        assertFalse(form.password.canSubmit)

        form.password.text = "1234567\uD83D\uDD11"

        assertEquals(8, form.password.length)
        assertTrue(form.password.canSubmit)
    }
}
