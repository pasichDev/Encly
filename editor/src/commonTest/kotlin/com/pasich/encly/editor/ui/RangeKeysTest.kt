package com.pasich.encly.editor.ui

import androidx.compose.ui.input.key.Key
import com.pasich.encly.editor.state.HeadMove
import com.pasich.encly.editor.ui.blocks.headMove
import com.pasich.encly.editor.ui.blocks.noteMove
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RangeKeysTest {
    @Test
    fun theArrowsMoveByLogicalDirectionSoRightToLeftTextSwapsLeftAndRight() {
        assertEquals(HeadMove.PREVIOUS, Key.DirectionLeft.headMove(rtl = false))
        assertEquals(HeadMove.NEXT, Key.DirectionRight.headMove(rtl = false))
        assertEquals(HeadMove.NEXT, Key.DirectionLeft.headMove(rtl = true))
        assertEquals(HeadMove.PREVIOUS, Key.DirectionRight.headMove(rtl = true))
    }

    @Test
    fun theOtherMovementKeys() {
        assertEquals(HeadMove.UP, Key.DirectionUp.headMove(rtl = false))
        assertEquals(HeadMove.DOWN, Key.DirectionDown.headMove(rtl = false))
        assertEquals(HeadMove.FIELD_START, Key.MoveHome.headMove(rtl = false))
        assertEquals(HeadMove.FIELD_END, Key.MoveEnd.headMove(rtl = false))
        assertEquals(HeadMove.PAGE_UP, Key.PageUp.headMove(rtl = false))
        assertEquals(HeadMove.PAGE_DOWN, Key.PageDown.headMove(rtl = false))
        assertNull(Key.A.headMove(rtl = false))
    }

    @Test
    fun homeAndEndWithControlGoToTheEndsOfTheNote() {
        assertEquals(HeadMove.NOTE_START, Key.MoveHome.noteMove())
        assertEquals(HeadMove.NOTE_END, Key.MoveEnd.noteMove())
        assertNull(Key.DirectionLeft.noteMove())
    }
}
