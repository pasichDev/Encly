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

    var pin by mutableStateOf("")
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
        if (pin.length >= PIN_LENGTH || lockedOut) return
        if (pin.isEmpty()) pinError = null
        pin += digit
    }

    fun deleteDigit() {
        if (pin.isNotEmpty()) pin = pin.dropLast(1)
    }

    /** The full PIN, taken out of the form to be checked. */
    fun takePin(): String = pin.also { pin = "" }

    fun onPinResult(result: PinUnlockResult, lockoutRemainingMillis: Long) {
        when (result) {
            PinUnlockResult.WRONG_PIN -> {
                pinError = R.string.lock_wrong_pin
                shakeKey++
                lockoutSeconds = lockoutSecondsLeft(lockoutRemainingMillis)
            }

            PinUnlockResult.DB_ERROR -> pinError = R.string.error_database_open

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
}
