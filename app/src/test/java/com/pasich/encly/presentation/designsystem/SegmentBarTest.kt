package com.pasich.encly.presentation.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The segment bar's layout: never wider than its 160 dp, and honest about done and open. */
class SegmentBarTest {
    // 160 dp at 1x: 1 px segments 1 px apart, so 80 fit.
    private val width = 160f
    private val min = 1f

    @Test
    fun segmentsThatFitAreDrawnOneBySubtaskAsTheyAre() {
        val done = listOf(true, false, true, false)

        assertSame(done, segmentsToDraw(done, width, min, min))
    }

    @Test
    fun moreSubtasksThanFitAreDrawnAsFewerSegmentsWithinTheWidth() {
        val done = List(200) { it % 4 == 0 }

        val drawn = segmentsToDraw(done, width, min, min)

        assertEquals(80, drawn.size)
        // 80 segments and 79 gaps of at least 1 px each stay inside 160 px.
        assertTrue(drawn.size * min + (drawn.size - 1) * min <= width)
        // A quarter done: 20 of 80, done ones first.
        assertEquals(List(80) { it < 20 }, drawn)
    }

    @Test
    fun oneDoneOrOneOpenAmongManyStillShows() {
        val oneDone = List(500) { it == 7 }
        val oneOpen = List(500) { it != 7 }

        assertEquals(1, segmentsToDraw(oneDone, width, min, min).count { it })
        assertEquals(1, segmentsToDraw(oneOpen, width, min, min).count { !it })
    }

    @Test
    fun allDoneOrNoneDoneStaysSo() {
        assertTrue(segmentsToDraw(List(300) { true }, width, min, min).all { it })
        assertTrue(segmentsToDraw(List(300) { false }, width, min, min).none { it })
    }
}
