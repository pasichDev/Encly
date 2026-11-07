package com.pasich.encly.presentation.screen

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.error_database_open
import com.pasich.encly.core.resources.lock_pin_key_lost
import com.pasich.encly.core.resources.lock_wrong_password
import com.pasich.encly.core.resources.lock_wrong_pin
import com.pasich.encly.core.resources.lock_wrong_recovery_phrase
import com.pasich.encly.presentation.designsystem.RecoveryPhraseState
import com.pasich.encly.presentation.screen.pincode.PasswordInput
import com.pasich.encly.presentation.screen.pincode.PinBuffer
import com.pasich.encly.presentation.screen.pincode.lockoutSecondsLeft
import com.pasich.encly.presentation.viewmodel.PinUnlockResult
import com.pasich.encly.presentation.viewmodel.SeedUnlockResult
import org.jetbrains.compose.resources.StringResource

/**
 * What the lock screen's forms show: the PIN typed so far, the password, the recovery phrase, and the last
 * error of each. Held by [LockScreen] itself, above the "Unlocking…" view that replaces the
 * forms while a credential is checked, so a wrong PIN or phrase is still shown afterwards
 * instead of being lost with the form that asked for it.
 */
@Stable
@Suppress("TooManyFunctions") // One small handler per field of the PIN, password and phrase forms.
internal class LockFormState {
    /** The recovery-phrase form is shown instead of the PIN pad. */
    var useRecovery by mutableStateOf(false)

    /** The password form is shown instead of the PIN pad (the user switched, or the vault has no PIN). */
    var usePassword by mutableStateOf(false)

    /** The password typed so far; a String in the text field, dropped when taken or cleared (see [PasswordInput]). */
    val password = PasswordInput()

    var passwordError by mutableStateOf<StringResource?>(null)
        private set

    /** How many digits the PIN had when it was sent to be checked: the dots stay full meanwhile. */
    var submittedLength by mutableIntStateOf(0)
        private set

    /** The digits typed so far, wiped when taken or cleared (see [PinBuffer]). */
    private val pinDigits = PinBuffer()

    /** How many PIN digits are typed. */
    val pinLength: Int get() = pinDigits.length

    var pinError by mutableStateOf<StringResource?>(null)
        private set

    /** Changes on every wrong PIN, so the dots shake again. */
    var shakeKey by mutableIntStateOf(0)
        private set

    var lockoutSeconds by mutableLongStateOf(0L)

    /** The recovery words typed so far. */
    val phrase = RecoveryPhraseState()

    var phraseError by mutableStateOf<StringResource?>(null)
        private set

    val lockedOut: Boolean get() = lockoutSeconds > 0L

    /** A digit on the keypad; the first digit of a new attempt clears the last error. */
    fun typeDigit(digit: Int) {
        if (pinDigits.isFull || lockedOut || digit !in 0..MAX_DIGIT) return
        if (pinLength == 0) pinError = null
        pinDigits.add(digit)
    }

    fun deleteDigit() = pinDigits.deleteLast()

    /** Enough digits are typed to be a PIN; the Unlock button is on from here, nothing is sent by itself. */
    val canSubmitPin: Boolean get() = pinDigits.canSubmit

    /** The PIN, taken out of the form to be checked. The caller wipes the copy. */
    fun takePin(): CharArray {
        submittedLength = pinDigits.length
        return pinDigits.take()
    }

    /** Forgets the typed digits. */
    fun clearPin() = pinDigits.clear()

    /** The password, taken out of the form to be checked. The caller wipes the copy. */
    fun takePassword(): CharArray = password.take()

    /** Any edit of the password clears the last error. */
    fun onPasswordEdited() {
        passwordError = null
    }

    /** The PIN pad and the password form swap places; what was typed in the one is dropped. */
    fun switchTo(usePassword: Boolean) {
        clearPin()
        password.clear()
        pinError = null
        passwordError = null
        this.usePassword = usePassword
    }

    fun onPasswordResult(result: PinUnlockResult, lockoutRemainingMillis: Long) {
        when (result) {
            PinUnlockResult.WRONG_PIN -> {
                passwordError = Res.string.lock_wrong_password
                lockoutSeconds = lockoutSecondsLeft(lockoutRemainingMillis)
            }

            PinUnlockResult.DB_ERROR -> passwordError = Res.string.error_database_open

            PinUnlockResult.LOCKED_OUT -> lockoutSeconds = lockoutSecondsLeft(lockoutRemainingMillis)

            PinUnlockResult.KEY_LOST -> {
                passwordError = Res.string.lock_pin_key_lost
                phraseError = Res.string.lock_pin_key_lost
            }

            PinUnlockResult.SUCCESS, PinUnlockResult.BACKGROUNDED -> Unit
        }
    }

    fun onPinResult(result: PinUnlockResult, lockoutRemainingMillis: Long) {
        when (result) {
            PinUnlockResult.WRONG_PIN -> {
                pinError = Res.string.lock_wrong_pin
                shakeKey++
                lockoutSeconds = lockoutSecondsLeft(lockoutRemainingMillis)
            }

            PinUnlockResult.DB_ERROR -> pinError = Res.string.error_database_open

            PinUnlockResult.LOCKED_OUT -> lockoutSeconds = lockoutSecondsLeft(lockoutRemainingMillis)

            // The PIN can no longer unlock here: the recovery form says why, when there is one.
            PinUnlockResult.KEY_LOST -> {
                pinError = Res.string.lock_pin_key_lost
                phraseError = Res.string.lock_pin_key_lost
            }

            PinUnlockResult.SUCCESS, PinUnlockResult.BACKGROUNDED -> Unit
        }
    }

    /** Any edit of the words clears the last error. */
    fun onPhraseEdited() {
        phraseError = null
    }

    /** System back: from the recovery form to the PIN pad or password form; those themselves stay. */
    fun back() {
        if (useRecovery) leaveRecovery()
    }

    /** Back to the PIN pad (or the password form); the typed words are dropped. */
    fun leaveRecovery() {
        useRecovery = false
        phrase.clear()
        phraseError = null
    }

    /** A wrong phrase stays in the field, with the error under it, so it can be corrected. */
    fun onSeedResult(result: SeedUnlockResult) {
        when (result) {
            SeedUnlockResult.WRONG_SEED -> phraseError = Res.string.lock_wrong_recovery_phrase
            SeedUnlockResult.DB_ERROR -> phraseError = Res.string.error_database_open
            SeedUnlockResult.SUCCESS, SeedUnlockResult.BACKGROUNDED -> Unit
        }
    }

    private companion object {
        const val MAX_DIGIT = 9
    }
}
