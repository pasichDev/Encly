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

    @Test
    fun backspaceAtTheStartOfTextJoinsFirstAndAnEmptyFieldIsRemovedOnlyIfNothingJoined() {
        var joins = 0
        var joined = false
        val joining = KeyboardUtils.KeyHandlers(
            onBackspaceEmpty = { backspaces++ },
            onBackspaceAtStart = {
                joins++
                joined
            },
        )
        fun backspace(text: String, cursor: Int) =
            KeyboardUtils.handleKey(Key.Backspace, KeyEventType.KeyDown, text, cursor, joining)

        assertFalse(backspace("milk", cursor = 2))
        assertEquals(0, joins)

        joined = true
        assertTrue(backspace("milk", cursor = 0))
        assertTrue(backspace("", cursor = 0))
        assertEquals(0, backspaces)

        joined = false
        assertFalse(backspace("milk", cursor = 0))
        assertTrue(backspace("", cursor = 0))
        assertEquals(1, backspaces)
        assertEquals(4, joins)
    }

    @Test
    fun backspaceWithASelectionIsLeftToTheField() {
        assertFalse(KeyboardUtils.handleKey(Key.Backspace, KeyEventType.KeyDown, "milk", -1, handlers))
    }

    private var moves = mutableListOf<String>()
    private val navigating = KeyboardUtils.KeyHandlers(
        onNavigateUp = { moves.add("up").let { true } },
        onNavigateDown = { moves.add("down").let { true } },
        onNavigateLeft = { moves.add("left").let { true } },
        onNavigateRight = { moves.add("right").let { true } },
    )

    private fun arrow(key: Key, cursor: Int, text: String = "milk", line: CaretLine? = null) =
        KeyboardUtils.handleKey(key, KeyEventType.KeyDown, text, cursor, navigating, line)

    @Test
    fun upAndDownLeaveTheFieldOnlyFromItsFirstAndLastLine() {
        val middle = CaretLine(onFirst = false, onLast = false, x = 5f)
        assertFalse(arrow(Key.DirectionUp, cursor = 2, line = middle))
        assertFalse(arrow(Key.DirectionDown, cursor = 2, line = middle))
        assertTrue(arrow(Key.DirectionUp, cursor = 2, line = CaretLine(true, false, 5f)))
        assertTrue(arrow(Key.DirectionDown, cursor = 2, line = CaretLine(false, true, 5f)))
        assertEquals(listOf("up", "down"), moves)
    }

    @Test
    fun withoutALayoutOnlyTheEndsOfTheTextCount() {
        assertFalse(arrow(Key.DirectionUp, cursor = 2))
        assertTrue(arrow(Key.DirectionUp, cursor = 0))
        assertTrue(arrow(Key.DirectionDown, cursor = 4))
    }

    @Test
    fun leftAtTheStartAndRightAtTheEndMoveOnAndAnywhereElseStayInTheField() {
        assertTrue(arrow(Key.DirectionLeft, cursor = 0))
        assertTrue(arrow(Key.DirectionRight, cursor = 4))
        assertFalse(arrow(Key.DirectionLeft, cursor = 1))
        assertFalse(arrow(Key.DirectionRight, cursor = 3))
        assertFalse(arrow(Key.DirectionLeft, cursor = -1))
        assertEquals(listOf("left", "right"), moves)
    }

    @Test
    fun anArrowWithAModifierNeverLeavesTheField() {
        assertTrue(KeyboardUtils.keepsArrow(Key.DirectionDown, modified = true))
        assertFalse(KeyboardUtils.keepsArrow(Key.DirectionDown, modified = false))
        assertFalse(KeyboardUtils.keepsArrow(Key.Backspace, modified = true))
    }

    @Test
    fun inRightToLeftTextLeftLeavesAtTheEndAndRightAtTheStart() {
        val rtl = CaretLine(onFirst = true, onLast = true, x = 0f, rtl = true)

        assertFalse(arrow(Key.DirectionLeft, cursor = 0, line = rtl))
        assertTrue(arrow(Key.DirectionLeft, cursor = 4, line = rtl))
        assertTrue(arrow(Key.DirectionRight, cursor = 0, line = rtl))
        // Right at the start of right-to-left text goes back, to the block above.
        assertEquals(listOf("right", "left"), moves)
    }
}
