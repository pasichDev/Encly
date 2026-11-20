package com.pasich.encly.presentation.components.editNote

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import com.pasich.encly.ui.theme.EnclyTheme
import kotlinx.coroutines.delay

/**
 * Keeps the soft keyboard up while a block field is replaced under it. Converting or undoing a
 * block disposes the focused field before its successor exists, and a moment with nothing focused
 * hides the keyboard (it then shows again for the new field). [hold] hands focus to a hidden field of
 * the toolbar's own in the same frame - moving between two text fields keeps the keyboard - and the
 * next block field to take focus takes it back. Should none do so in time, focus is cleared, so typing
 * never goes into the hidden field.
 */
@Stable
internal class KeyboardAnchor {
    internal val requester = FocusRequester()
    internal var holding by mutableStateOf(false)
    internal var focused = false

    /**
     * Runs [change] without letting the keyboard drop where it removes the field that had focus
     * ([focusedBlock] before the change; [exists] tells whether a block is still there). A field that
     * stays keeps its focus, and the anchor stays out of the way.
     */
    fun around(focusedBlock: String?, exists: (String) -> Boolean, change: () -> Unit) {
        change()
        if (focusedBlock != null && !exists(focusedBlock)) hold()
    }

    fun hold() {
        holding = true
        runCatching { requester.requestFocus() }
    }
}

@Composable
internal fun rememberKeyboardAnchor() = remember { KeyboardAnchor() }

/** The hidden field of a [KeyboardAnchor]: a 1 dp invisible text field, out of TalkBack's reach. */
@Composable
internal fun KeyboardAnchorField(anchor: KeyboardAnchor, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    LaunchedEffect(anchor.holding) {
        if (!anchor.holding) return@LaunchedEffect
        // A block field takes focus again, normally within a few frames, and that takes it from the
        // anchor. Where none does (the target cannot take focus, or never appears), focus is cleared,
        // as it would have been without the anchor: the hidden field never keeps the keyboard.
        delay(HOLD_MS)
        if (anchor.focused) focusManager.clearFocus()
        anchor.holding = false
    }
    BasicTextField(
        state = remember { TextFieldState() },
        modifier = modifier
            .size(EnclyTheme.spacing.hairline)
            .graphicsLayer { alpha = 0f }
            .focusRequester(anchor.requester)
            // Only while it holds: Tab or a D-pad never lands in it.
            .focusProperties { canFocus = anchor.holding }
            .onFocusChanged { anchor.focused = it.isFocused }
            .semantics { hideFromAccessibility() },
    )
}

/** How long the anchor waits for a block field to take focus back. */
private const val HOLD_MS = 600L
