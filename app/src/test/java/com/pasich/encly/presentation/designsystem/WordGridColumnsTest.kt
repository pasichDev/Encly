package com.pasich.encly.presentation.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Widths in dp at density 1: a 360 dp phone less the 24 dp gutters leaves 312 for the grid, an
 * 8 dp gap and 56 dp of cell chrome (padding, number, gap), so 96 dp for a word in two columns.
 * "mushroom" in dataLarge is about 90 dp at font scale 1.0 and 117 dp at 1.3.
 */
class WordGridColumnsTest {

    @Test
    fun theWidestWordFitsTwoColumns() {
        assertEquals(2, wordGridColumns(availableWidthPx = 312, gapPx = 8, cellChromePx = 56, longestWordPx = 90))
        assertEquals(2, wordGridColumns(availableWidthPx = 312, gapPx = 8, cellChromePx = 56, longestWordPx = 96))
    }

    @Test
    fun aWordThatWouldBeCutMovesTheGridToOneColumn() {
        // Font scale 1.3 on a 360 dp phone.
        assertEquals(1, wordGridColumns(availableWidthPx = 312, gapPx = 8, cellChromePx = 56, longestWordPx = 117))
        // Font scale 1.0 on a 320 dp screen (display size Large): 76 dp of room.
        assertEquals(1, wordGridColumns(availableWidthPx = 272, gapPx = 8, cellChromePx = 56, longestWordPx = 90))
    }
}
