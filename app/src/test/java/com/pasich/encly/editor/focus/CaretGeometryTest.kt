package com.pasich.encly.editor.focus

import com.pasich.encly.editor.state.CaretMove
import com.pasich.encly.editor.state.FocusRequest
import com.pasich.encly.editor.state.goesUp
import com.pasich.encly.editor.state.requestFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaretGeometryTest {
    /** Lines of [width] characters, [charWidth] pixels each. */
    private class Grid(val length: Int, val width: Int, val charWidth: Float = 10f) : CaretGeometry {
        override val lineCount: Int get() = (length + width - 1) / width
        override val textLength: Int get() = length
        override fun isRtl(offset: Int) = false
        override fun lineOf(offset: Int): Int = minOf(offset / width, lineCount - 1)
        override fun xOf(offset: Int): Float = (offset - lineOf(offset) * width) * charWidth
        override fun offsetAt(line: Int, x: Float): Int =
            minOf(line * width + (x / charWidth).toInt(), minOf(length, (line + 1) * width))
    }

    @Test
    fun theCursorKnowsWhichLineItIsOn() {
        val grid = Grid(length = 25, width = 10)

        assertEquals(CaretLine(onFirst = true, onLast = false, x = 30f), caretLine(grid, 25, 3))
        assertEquals(CaretLine(onFirst = false, onLast = false, x = 40f), caretLine(grid, 25, 14))
        assertEquals(CaretLine(onFirst = false, onLast = true, x = 50f), caretLine(grid, 25, 25))
    }

    @Test
    fun aLayoutOfOtherTextIsIgnoredInsteadOfBeingAskedAboutAnOffsetItDoesNotHave() {
        val stale = Grid(length = 5, width = 10)

        assertEquals(CaretLine(onFirst = false, onLast = true, x = null), caretLine(stale, length = 9, cursor = 9))
    }

    @Test
    fun withoutALayoutOnlyTheTextEndsCount() {
        assertEquals(CaretLine(true, false, null), caretLine(null, 5, 0))
        assertEquals(CaretLine(false, true, null), caretLine(null, 5, 5))
    }

    @Test
    fun arrivingFromBelowLandsOnTheLastLineAtTheColumn() {
        val grid = Grid(length = 25, width = 10)

        assertEquals(23, caretOffsetFor(grid, x = 30f, fromBelow = true, length = 25))
        assertEquals(3, caretOffsetFor(grid, x = 30f, fromBelow = false, length = 25))
        // A short last line clamps to its end.
        assertEquals(25, caretOffsetFor(grid, x = 90f, fromBelow = true, length = 25))
    }

    @Test
    fun withoutALayoutTheCursorGoesToTheNearEnd() {
        assertEquals(5, caretOffsetFor(null, 30f, fromBelow = true, length = 5))
        assertEquals(0, caretOffsetFor(null, 30f, fromBelow = false, length = 5))
    }

    @Test
    fun movesBecomeFocusRequests() {
        assertEquals(FocusRequest("b", caretX = 4f, fromBelow = true), CaretMove.Up(4f).requestFor("b"))
        assertEquals(FocusRequest("b", cursorToEnd = true, fromBelow = true), CaretMove.Up(null).requestFor("b"))
        assertEquals(FocusRequest("b", caretX = 4f), CaretMove.Down(4f).requestFor("b"))
        assertEquals(FocusRequest("b", caret = 0), CaretMove.Right.requestFor("b"))
        assertEquals(FocusRequest("b", cursorToEnd = true, fromBelow = true), CaretMove.Left.requestFor("b"))
        assertTrue(CaretMove.Left.goesUp)
        assertFalse(CaretMove.Right.goesUp)
    }
}
