package com.pasich.encly.dynamicBlocks.focus

import com.pasich.encly.core.AppLogger
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key

/**
 * Utilities for handling keyboard events in the block editor.
 */
object KeyboardUtils {
    /**
     * Handles keyboard events for navigation between blocks.
     */
    fun handleKeyEvent(
        event: KeyEvent,
        text: String = "",
        cursorPosition: Int = 0,
        onBackspaceEmpty: () -> Unit = {},
        onNavigateUp: () -> Boolean = { false },
        onNavigateDown: () -> Boolean = { false },
        onEnterPressed: () -> Boolean = { false },
    ): Boolean =
        when {
            event.key == Key.DirectionUp -> {
                if (text.isEmpty() || cursorPosition == 0) {
                    onNavigateUp()
                } else {
                    false
                }
            }

            event.key == Key.DirectionDown -> {
                if (text.isEmpty() || cursorPosition == text.length) {
                    onNavigateDown()
                } else {
                    false
                }
            }

            event.key == Key.Enter -> {
                AppLogger.d("KeyboardUtils", "Enter pressed")
                onEnterPressed()
            }

            event.key.keyCode == Key.Backspace.keyCode -> {
                AppLogger.d(
                    "KeyboardUtils",
                    "Backspace pressed (keyCode=${event.nativeKeyEvent.keyCode}), text empty: ${text.isEmpty()}",
                )
                if (text.isEmpty()) {
                    AppLogger.d("KeyboardUtils", "Calling onBackspaceEmpty")
                    onBackspaceEmpty()
                    true
                } else {
                    false
                }
            }

            else -> false
        }
}
