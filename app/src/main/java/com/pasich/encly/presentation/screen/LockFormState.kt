package com.pasich.encly.presentation.screen

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pasich.encly.R
import com.pasich.encly.core.security.PIN_LENGTH
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.pasich.encly.presentation.designsystem.RecoveryPhraseState
import com.pasich.encly.presentation.screen.pincode.lockoutSecondsLeft
import com.pasich.encly.presentation.viewmodel.PinUnlockResult
import com.pasich.encly.presentation.viewmodel.SeedUnlockResult

/**
 * What the lock screen's forms show: the PIN typed so far, the recovery phrase, and the last
 * error of each. Held by [LockScreen] itself, above the "Unlocking…" view that replaces the
 * forms while a credential is checked, so a wrong PIN or phrase is still shown afterwards
 * instead of being lost with the form that asked for it.
 */
@Stable
internal class LockFormState {
    /** The recovery-phrase form is shown instead of the PIN pad. */
    var useRecovery by mutableStateOf(false)

    /**
     * The digits typed so far live in a CharArray that is wiped when taken or cleared, never
     * in a String: only their count is Compose state.
     */
    private val pinDigits = CharArray(PIN_LENGTH)

    /** How many PIN digits are typed. */
    var pinLength by mutableIntStateOf(0)
        private set

    @get:StringRes
    var pinError by mutableStateOf<Int?>(null)
        private set

    /** Changes on every wrong PIN, so the dots shake again. */
    var shakeKey by mutableIntStateOf(0)
        private set

    var lockoutSeconds by mutableLongStateOf(0L)

    /** The recovery words typed so far. */
    val phrase = RecoveryPhraseState()

    @get:StringRes
    var phraseError by mutableStateOf<Int?>(null)
        private set

    val lockedOut: Boolean get() = lockoutSeconds > 0L

    /** A digit on the keypad; the first digit of a new attempt clears the last error. */
    fun typeDigit(digit: Int) {
        if (pinLength >= PIN_LENGTH || lockedOut || digit !in 0..MAX_DIGIT) return
        if (pinLength == 0) pinError = null
        pinDigits[pinLength] = '0' + digit
        pinLength++
    }

    fun deleteDigit() {
        if (pinLength > 0) {
            pinLength--
            pinDigits[pinLength] = '\u0000'
        }
    }

    /** The full PIN, taken out of the form to be checked. The caller wipes the copy. */
    fun takePin(): CharArray = pinDigits.copyOf(pinLength).also { clearPin() }

    /** Forgets the typed digits. */
    fun clearPin() {
        SensitiveDataCleaner.clear(pinDigits)
        pinLength = 0
    }

    fun onPinResult(result: PinUnlockResult, lockoutRemainingMillis: Long) {
        when (result) {
            PinUnlockResult.WRONG_PIN -> {
                pinError = R.string.lock_wrong_pin
                shakeKey++
                lockoutSeconds = lockoutSecondsLeft(lockoutRemainingMillis)
            }

            PinUnlockResult.DB_ERROR -> pinError = R.string.error_database_open

            PinUnlockResult.LOCKED_OUT -> lockoutSeconds = lockoutSecondsLeft(lockoutRemainingMillis)

            // The PIN can no longer unlock here: the recovery form says why, when there is one.
            PinUnlockResult.KEY_LOST -> {
                pinError = R.string.lock_pin_key_lost
                phraseError = R.string.lock_pin_key_lost
            }

            PinUnlockResult.SUCCESS, PinUnlockResult.BACKGROUNDED -> Unit
        }
    }

    /** Any edit of the words clears the last error. */
    fun onPhraseEdited() {
        phraseError = null
    }

    /** System back: from the recovery form to the PIN pad; the PIN pad itself stays. */
    fun back() {
        if (useRecovery) leaveRecovery()
    }

    /** Back to the PIN pad; the typed words are dropped. */
    fun leaveRecovery() {
        useRecovery = false
        phrase.clear()
        phraseError = null
    }

    /** A wrong phrase stays in the field, with the error under it, so it can be corrected. */
    fun onSeedResult(result: SeedUnlockResult) {
        when (result) {
            SeedUnlockResult.WRONG_SEED -> phraseError = R.string.lock_wrong_recovery_phrase
            SeedUnlockResult.DB_ERROR -> phraseError = R.string.error_database_open
            SeedUnlockResult.SUCCESS, SeedUnlockResult.BACKGROUNDED -> Unit
        }
    }

    private companion object {
        const val MAX_DIGIT = 9
    }
}
