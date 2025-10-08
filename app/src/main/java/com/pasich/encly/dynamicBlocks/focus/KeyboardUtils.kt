package com.pasich.encly.dynamicBlocks.focus

import android.util.Log
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key

/**
 * Утиліти для обробки клавіатурних подій в блочному редакторі
 */
object KeyboardUtils {
    /**
     * Обробляє клавіатурні події для навігації між блоками
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
                Log.d("KeyboardUtils", "Enter pressed")
                onEnterPressed()
            }

            event.key.keyCode == Key.Backspace.keyCode -> {
                Log.d(
                    "KeyboardUtils",
                    "Backspace pressed (keyCode=${event.nativeKeyEvent.keyCode}), text empty: ${text.isEmpty()}",
                )
                if (text.isEmpty()) {
                    Log.d("KeyboardUtils", "Calling onBackspaceEmpty")
                    onBackspaceEmpty()
                    true
                } else {
                    false
                }
            }

            else -> false
        }
}
