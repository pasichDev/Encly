package com.pasich.encly.editor.ui.blocks

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange

/**
 * Makes a tap anywhere on a block's card, its padding included, focus the card's text field. The field's own taps
 * place the cursor where they land; a tap beside or around the text puts it at the start (upper half of the card)
 * or at the end (lower half). Without this only the text itself takes taps.
 */
internal fun Modifier.tapCardToFocus(state: TextFieldState, focusRequester: FocusRequester): Modifier =
    pointerInput(state, focusRequester) {
        detectTapGestures { offset ->
            val atStart = offset.y < size.height / 2f
            // A tap on the text itself reaches the field first, which has then focused already and placed the
            // cursor; the card only acts when the field has not taken the tap.
            runCatching { focusRequester.requestFocus() }
            state.edit { selection = TextRange(if (atStart) 0 else length) }
        }
    }
