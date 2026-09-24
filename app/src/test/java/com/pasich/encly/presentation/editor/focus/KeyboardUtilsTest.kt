package com.pasich.encly.presentation.editor.focus

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardUtilsTest {
    private var enters = 0
    private var backspaces = 0
    private val handlers = KeyboardUtils.KeyHandlers(
        onBackspaceEmpty = { backspaces++ },
        onEnterPressed = {
            enters++
            true
        },
    )

    private fun press(key: Key, type: KeyEventType, text: String = "milk") =
        KeyboardUtils.handleKey(key, type, text, cursorPosition = text.length, handlers = handlers)

    @Test
    fun enterActsOnKeyDownAndConsumesItSoTheFieldTypesNoLineBreak() {
        // A hardware Enter in a list item: the key-down must be consumed before the text field
        // sees it, or the field inserts "\n" and the item is added on the key-up after it.
        assertTrue(press(Key.Enter, KeyEventType.KeyDown))
        assertEquals(1, enters)
    }

    @Test
    fun onePressActsOnce() {
        press(Key.Enter, KeyEventType.KeyDown)
        assertFalse(press(Key.Enter, KeyEventType.KeyUp))

        press(Key.Backspace, KeyEventType.KeyDown, text = "")
        press(Key.Backspace, KeyEventType.KeyUp, text = "")

        assertEquals(1, enters)
        assertEquals(1, backspaces)
    }

    @Test
    fun backspaceInTextIsLeftToTheField() {
        assertFalse(press(Key.Backspace, KeyEventType.KeyDown, text = "milk"))
        assertEquals(0, backspaces)
    }
}
