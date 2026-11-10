package com.pasich.encly.editor.state

import com.pasich.encly.editor.model.Block
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectedBlocksTest {
    private val blocks = mutableListOf<Block>(
        text("a"),
        text("b"),
        text("c"),
        text("d"),
        text("e"),
    )
    private val picks = SelectedBlocks(blocks)

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    private fun ids(vararg at: Int) = at.map { blocks[it].id }.toSet()

    @Test
    fun nothingIsPickedAtFirst() {
        assertFalse(picks.isActive)
        assertEquals(0, picks.count)
        assertTrue(picks.picked().isEmpty())
    }

    @Test
    fun selectingABlockStartsTheModeWithOnlyThatBlock() {
        picks.select(blocks[1].id)
        picks.select(blocks[3].id)

        assertTrue(picks.isActive)
        assertEquals(ids(3), picks.ids.value)
        assertEquals(1, picks.count)
    }

    @Test
    fun aBlockThatIsNotInTheNoteCannotBePicked() {
        picks.select("gone")
        picks.toggle("gone")

        assertFalse(picks.isActive)
    }

    @Test
    fun togglingPicksAndReleasesAndTheModeEndsWithTheLastPick() {
        picks.select(blocks[0].id)
        picks.toggle(blocks[2].id)
        assertEquals(ids(0, 2), picks.ids.value)

        picks.toggle(blocks[0].id)
        assertEquals(ids(2), picks.ids.value)

        picks.toggle(blocks[2].id)
        assertFalse(picks.isActive)
    }

    @Test
    fun togglingABlockOfAnEmptySelectionStartsARangeThere() {
        picks.toggle(blocks[2].id)
        picks.extend(up = false)

        assertEquals(ids(2, 3), picks.ids.value)
    }

    @Test
    fun theRangeKeepsItsEndsWhenAnInnerPickIsReleased() {
        picks.select(blocks[0].id)
        picks.toggle(blocks[1].id)
        picks.toggle(blocks[2].id)
        picks.toggle(blocks[1].id)

        // The head is still the last pick: extending goes on from it.
        picks.extend(up = false)

        assertEquals(ids(0, 2, 3), picks.ids.value)
    }

    @Test
    fun releasingTheAnchorMovesTheAnchorToAPick() {
        picks.select(blocks[0].id)
        picks.toggle(blocks[2].id)
        picks.toggle(blocks[0].id)

        // The anchor is now block 2 (the only pick): shrinking up from head 2 is not possible, extending is.
        picks.extend(up = true)

        assertEquals(ids(1, 2), picks.ids.value)
    }

    @Test
    fun selectAllPicksEveryBlockInOrder() {
        picks.selectAll()

        assertEquals(blocks.map { it.id }, picks.picked().map { it.id })
        assertEquals(5, picks.count)
    }

    @Test
    fun extendingGrowsTheRangeBothWaysAndShrinksItBack() {
        picks.select(blocks[2].id)

        picks.extend(up = false)
        picks.extend(up = false)
        assertEquals(ids(2, 3, 4), picks.ids.value)

        picks.extend(up = true)
        assertEquals(ids(2, 3), picks.ids.value)

        picks.extend(up = true)
        picks.extend(up = true)
        assertEquals(ids(1, 2), picks.ids.value)

        picks.extend(up = true)
        picks.extend(up = true)
        assertEquals(ids(0, 1, 2), picks.ids.value)
    }

    @Test
    fun extendingStopsAtTheEndsOfTheNote() {
        picks.select(blocks[0].id)
        picks.extend(up = true)
        assertEquals(ids(0), picks.ids.value)

        picks.select(blocks[4].id)
        picks.extend(up = false)
        assertEquals(ids(4), picks.ids.value)
    }

    @Test
    fun extendingDoesNothingOutsideTheMode() {
        picks.extend(up = false)

        assertFalse(picks.isActive)
    }

    @Test
    fun clearingEndsTheModeAndForgetsTheRange() {
        picks.select(blocks[1].id)
        picks.clear()
        assertFalse(picks.isActive)

        picks.extend(up = false)
        assertFalse(picks.isActive)
    }

    @Test
    fun blocksThatLeftTheNoteDropOutAndTheRangeFollows() {
        picks.select(blocks[1].id)
        picks.extend(up = false)
        picks.extend(up = false)

        // Blocks 1 and 3 go away (an undo, a delete); 2 is still picked.
        val gone = blocks.removeAt(3)
        blocks.removeAt(1)
        picks.prune()

        assertEquals(setOf(blocks[1].id), picks.ids.value)
        assertFalse(gone.id in picks.ids.value)
        picks.extend(up = false)
        assertEquals(ids(1, 2), picks.ids.value)
    }

    @Test
    fun pruningWithEveryPickGoneEndsTheMode() {
        picks.select(blocks[1].id)
        blocks.removeAt(1)

        picks.prune()

        assertFalse(picks.isActive)
    }

    @Test
    fun pruningWhenNothingLeftLeavesTheRangeAlone() {
        picks.select(blocks[1].id)
        picks.extend(up = false)

        picks.prune()
        picks.prune()

        assertEquals(ids(1, 2), picks.ids.value)
    }

    @Test
    fun pruningOutsideTheModeDoesNothing() {
        picks.prune()

        assertFalse(picks.isActive)
    }

    @Test
    fun releasingTheHeadMovesTheHeadToAPickAndMovingBackShrinksDownward() {
        picks.select(blocks[0].id)
        picks.toggle(blocks[1].id)
        picks.toggle(blocks[1].id)

        // The head went with the released pick; the range goes on from the one left.
        picks.extend(up = false)
        assertEquals(ids(0, 1), picks.ids.value)

        picks.select(blocks[3].id)
        picks.extend(up = true)
        picks.extend(up = false)
        // Moving back towards the anchor lets go of the block it leaves.
        assertEquals(ids(3), picks.ids.value)
    }
}
