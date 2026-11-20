package com.pasich.encly.editor.focus

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Utilities for handling keyboard events in the block editor.
 */
internal object KeyboardUtils {
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
        onNavigateLeft: () -> Boolean = { false },
        onNavigateRight: () -> Boolean = { false },
        line: CaretLine? = null,
    ): Boolean = !keepsArrow(event.key, event.hasModifier()) && handleKey(
        key = event.key,
        type = event.type,
        text = text,
        cursorPosition = cursorPosition,
        handlers = KeyHandlers(
            onBackspaceEmpty = onBackspaceEmpty,
            onNavigateUp = onNavigateUp,
            onNavigateDown = onNavigateDown,
            onEnterPressed = onEnterPressed,
            onBackspaceAtStart = onBackspaceAtStart,
            onNavigateLeft = onNavigateLeft,
            onNavigateRight = onNavigateRight,
        ),
        line = line,
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
        val onNavigateLeft: () -> Boolean = { false },
        val onNavigateRight: () -> Boolean = { false },
    )

    /** Acts once per key press, on key-down; key-ups are left to the field, which ignores them. */
    internal fun handleKey(
        key: Key,
        type: KeyEventType,
        text: CharSequence,
        cursorPosition: Int,
        handlers: KeyHandlers,
        line: CaretLine? = null,
    ): Boolean {
        if (type != KeyEventType.KeyDown) return false
        return when {
            key.isArrow() -> handleArrow(key, text, cursorPosition, handlers, line)
            key == Key.Enter -> handlers.onEnterPressed()
            key.keyCode == Key.Backspace.keyCode -> handleBackspace(text, cursorPosition, handlers)
            else -> false
        }
    }

    /**
     * Up and Down leave the field only from its first or last visual line (elsewhere the field moves
     * the cursor a line), Left only from the start and Right only from the end. A selection
     * (cursorPosition -1) is not a cursor: the field moves within it. An empty field is left by any of them.
     */
    private fun handleArrow(
        key: Key,
        text: CharSequence,
        cursorPosition: Int,
        handlers: KeyHandlers,
        line: CaretLine?,
    ): Boolean {
        val vertical = key == Key.DirectionUp || key == Key.DirectionDown
        // In right-to-left text Left moves toward the end and Right toward the start.
        val towardStart = (key == Key.DirectionLeft) != (line?.rtl == true)
        val leaves = (cursorPosition >= 0 && atEdge(key, towardStart, text.length, cursorPosition, line)) ||
            (vertical && text.isEmpty())
        return leaves && when {
            key == Key.DirectionUp -> handlers.onNavigateUp()
            key == Key.DirectionDown -> handlers.onNavigateDown()
            towardStart -> handlers.onNavigateLeft()
            else -> handlers.onNavigateRight()
        }
    }

    /** Whether the cursor stands where [key] would take it out of the field. */
    private fun atEdge(key: Key, towardStart: Boolean, length: Int, cursor: Int, line: CaretLine?): Boolean = when {
        key == Key.DirectionUp -> line?.onFirst ?: (cursor == 0)
        key == Key.DirectionDown -> line?.onLast ?: (cursor == length)
        towardStart -> cursor == 0
        else -> cursor == length
    }

    /** An arrow with Shift, Ctrl, Alt or Meta held selects or jumps within the field: it never leaves it. */
    internal fun keepsArrow(key: Key, modified: Boolean) = modified && key.isArrow()

    private fun KeyEvent.hasModifier() = isShiftPressed || isCtrlPressed || isAltPressed || isMetaPressed

    private fun Key.isArrow() =
        this == Key.DirectionUp || this == Key.DirectionDown || this == Key.DirectionLeft || this == Key.DirectionRight

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
