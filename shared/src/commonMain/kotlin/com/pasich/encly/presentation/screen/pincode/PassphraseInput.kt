package com.pasich.encly.presentation.screen.pincode

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A passphrase being typed into a masked field. A text field keeps its text as a String, which cannot be wiped (the
 * recovery words are no different): it is held here only while the form is up, dropped the moment the passphrase is
 * taken for a check, and what is handed on is a CharArray the receiver wipes. Held with `remember`, never
 * `rememberSaveable`: the passphrase must not reach saved state.
 */
@Stable
internal class PassphraseInput(private val maxUnits: Int = MAX_TYPED_CHARS) {
    /** The text in the field. Never read by anything but the field and [peek]. */
    var text by mutableStateOf("")
        private set

    /** The field shows what is typed instead of dots. */
    var revealed by mutableStateOf(false)

    /** The text came from "Suggest a passphrase" (and was not cleared since): the screen explains what it is. */
    var suggested by mutableStateOf(false)
        private set

    /** Something is typed. Nothing is submitted by itself: the user presses the button or Enter. */
    val canSubmit: Boolean get() = text.isNotEmpty()

    /**
     * An edit from the field. Up to [maxUnits] UTF-16 units (the most the policy takes as typed) it is kept as it is:
     * a half of a pair that is waiting for its other half stays, and the rule says "too long" for the rest. Only what
     * is beyond [maxUnits] is cut, and the cut never leaves half of a pair.
     */
    fun edit(value: String) {
        text = if (value.length <= maxUnits) value else value.take(maxUnits).dropLastWhile { it.isHighSurrogate() }
    }

    /** Puts [chars] (a suggestion, which the caller wipes) in the field, in clear, to be read and written down. */
    fun fill(chars: CharArray) {
        text = chars.concatToString()
        revealed = true
        suggested = true
    }

    /** A copy of what is typed, for a check that may refuse it. The caller wipes the copy. */
    fun peek(): CharArray = text.toCharArray()

    /** What is typed, taken out of the field, which is emptied. The caller wipes the copy. */
    fun take(): CharArray = peek().also { clear() }

    /**
     * Hands a copy to [handler], which wipes it and says whether it used the passphrase: then the field is emptied,
     * otherwise (a refusal the user can fix by editing) the text stays.
     */
    fun submit(handler: (CharArray) -> Boolean) {
        if (canSubmit && handler(peek())) clear()
    }

    /** Forgets the typed text and puts the field back to masked. */
    fun clear() {
        text = ""
        revealed = false
        suggested = false
    }

    companion object {
        /** The most units the field takes unless the policy says otherwise (see [SecretChoice.maxTypedUnits]). */
        const val MAX_TYPED_CHARS = 256
    }
}
