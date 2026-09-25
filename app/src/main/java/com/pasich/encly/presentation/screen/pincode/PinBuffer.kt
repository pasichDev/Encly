package com.pasich.encly.presentation.screen.pincode

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.pasich.encly.core.security.PIN_LENGTH
import com.pasich.encly.core.security.SensitiveDataCleaner

/**
 * The PIN digits typed on a keypad. They live in a CharArray that is wiped when taken or
 * cleared, never in a String (a String cannot be wiped): only their count is Compose state.
 */
@Stable
internal class PinBuffer {
    private val digits = CharArray(PIN_LENGTH)

    /** How many digits are typed. */
    var length by mutableIntStateOf(0)
        private set

    val isFull: Boolean get() = length == PIN_LENGTH

    /** Adds [digit] (0-9); ignored once the PIN is complete. Returns whether it was added. */
    fun add(digit: Int): Boolean {
        if (length >= PIN_LENGTH || digit !in 0..MAX_DIGIT) return false
        digits[length] = '0' + digit
        length++
        return true
    }

    fun deleteLast() {
        if (length > 0) {
            length--
            digits[length] = '\u0000'
        }
    }

    /** The typed digits, taken out of the buffer, which is cleared. The caller wipes the copy. */
    fun take(): CharArray = digits.copyOf(length).also { clear() }

    /** Forgets the typed digits. */
    fun clear() {
        SensitiveDataCleaner.clear(digits)
        length = 0
    }

    private companion object {
        const val MAX_DIGIT = 9
    }
}
