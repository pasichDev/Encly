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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import com.pasich.encly.ui.theme.EnclyTheme
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps the soft keyboard up while a block field is replaced under it. Converting or undoing a
 * block disposes the focused field before its successor exists, and a moment with nothing focused
 * hides the keyboard (it then shows again for the new field). [hold] hands focus to a hidden field of
 * the toolbar's own in the same frame - moving between two text fields keeps the keyboard - and
 * [KeyboardAnchorField] gives it back as soon as a block field has focus again.
 */
@Stable
internal class KeyboardAnchor {
    internal val requester = FocusRequester()
    internal var holding by mutableStateOf(false)
    internal var focused = false

    /** Runs [change], which replaces the focused block's field, without letting the keyboard drop. */
    fun around(editing: Boolean, change: () -> Unit) {
        change()
        if (editing) hold()
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
internal fun KeyboardAnchorField(
    anchor: KeyboardAnchor,
    focusedBlockId: StateFlow<String?>,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    LaunchedEffect(anchor.holding) {
        if (!anchor.holding) return@LaunchedEffect
        // A block field takes focus again, normally within a few frames, and that takes it from the
        // anchor. Only when none does is focus cleared, as it would have been without the anchor.
        val taken = withTimeoutOrNull(HOLD_MS) { focusedBlockId.first { it != null } } != null
        if (!taken && anchor.focused) focusManager.clearFocus()
        anchor.holding = false
    }
    BasicTextField(
        state = remember { TextFieldState() },
        modifier = modifier
            .size(EnclyTheme.spacing.hairline)
            .graphicsLayer { alpha = 0f }
            .focusRequester(anchor.requester)
            .onFocusChanged { anchor.focused = it.isFocused }
            .semantics { invisibleToUser() },
    )
}

/** How long the anchor waits for a block field to take focus back. */
private const val HOLD_MS = 600L
