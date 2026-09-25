package com.pasich.encly.presentation.editor.focus

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Utilities for handling keyboard events in the block editor.
 */
object KeyboardUtils {
    /**
     * Handles keyboard events for navigation between blocks. Install it with `onPreviewKeyEvent`,
     * so it sees a key before the text field does: the field acts on key-down (Enter types a
     * line break), and a handler that only saw the key-up would run after the field already had.
     */
    fun handleKeyEvent(
        event: KeyEvent,
        text: CharSequence = "",
        cursorPosition: Int = 0,
        onBackspaceEmpty: () -> Unit = {},
        onNavigateUp: () -> Boolean = { false },
        onNavigateDown: () -> Boolean = { false },
        onEnterPressed: () -> Boolean = { false },
        onBackspaceAtStart: () -> Boolean = { false },
    ): Boolean = handleKey(
        key = event.key,
        type = event.type,
        text = text,
        cursorPosition = cursorPosition,
        handlers = KeyHandlers(onBackspaceEmpty, onNavigateUp, onNavigateDown, onEnterPressed, onBackspaceAtStart),
    )

    /**
     * What [handleKey] runs for each key it handles. Backspace with the cursor at the start runs
     * [onBackspaceAtStart] first (join with the block above); if that does nothing and the field
     * is empty, [onBackspaceEmpty].
     */
    internal class KeyHandlers(
        val onBackspaceEmpty: () -> Unit = {},
        val onNavigateUp: () -> Boolean = { false },
        val onNavigateDown: () -> Boolean = { false },
        val onEnterPressed: () -> Boolean = { false },
        val onBackspaceAtStart: () -> Boolean = { false },
    )

    /** Acts once per key press, on key-down; key-ups are left to the field, which ignores them. */
    internal fun handleKey(
        key: Key,
        type: KeyEventType,
        text: CharSequence,
        cursorPosition: Int,
        handlers: KeyHandlers,
    ): Boolean {
        if (type != KeyEventType.KeyDown) return false
        return when {
            key == Key.DirectionUp -> {
                if (text.isEmpty() || cursorPosition == 0) handlers.onNavigateUp() else false
            }

            key == Key.DirectionDown -> {
                if (text.isEmpty() || cursorPosition == text.length) handlers.onNavigateDown() else false
            }

            key == Key.Enter -> handlers.onEnterPressed()

            key.keyCode == Key.Backspace.keyCode -> handleBackspace(text, cursorPosition, handlers)

            else -> false
        }
    }

    private fun handleBackspace(text: CharSequence, cursorPosition: Int, handlers: KeyHandlers): Boolean = when {
        // Inside the text, or a selection to delete: the field's own Backspace.
        text.isNotEmpty() && cursorPosition != 0 -> false

        handlers.onBackspaceAtStart() -> true

        text.isEmpty() -> {
            handlers.onBackspaceEmpty()
            true
        }

        else -> false
    }
}
