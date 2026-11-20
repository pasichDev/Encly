package com.pasich.encly.editor.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The rules of nested list items: valid levels, subtrees, siblings, numbers, indenting and moving. */
class ListLevelsTest {
    private fun row(text: String, level: Int = 0, kind: BlockType? = null, check: Boolean = false) =
        ItemListBlock(text, isCheck = check, level = level, kind = kind)

    private fun List<ItemListBlock>.shape() = joinToString(" ") { "${it.value}${it.level}" }

    private val bullet = BlockType.LIST_BULLET
    private val number = BlockType.LIST_NUMBER
    private val check = BlockType.LIST_CHECK

    // a0 b1 c2 d1 e0
    private val tree = listOf(row("a"), row("b", 1), row("c", 2), row("d", 1), row("e"))

    @Test
    fun aListKindIsABulletedNumberedOrCheckList() {
        assertTrue(bullet.isListKind() && number.isListKind() && check.isListKind())
        assertTrue(!BlockType.TEXT.isListKind() && !BlockType.QUOTE.isListKind())
    }

    @Test
    fun aNestedRowHasItsOwnKindAndATopRowTheListsOwn() {
        assertEquals(bullet, row("a").kindIn(bullet))
        assertEquals(bullet, row("a", 1).kindIn(bullet))
        assertEquals(check, row("a", 1, check).kindIn(bullet))
        // A kind on a row at the top level is ignored.
        assertEquals(bullet, row("a", 0, check).kindIn(bullet))
    }

    // --- normalizedLevels ------------------------------------------------------------------------

    @Test
    fun validLevelsAreLeftAloneAndTheSameListIsReturned() {
        assertSame(tree, tree.normalizedLevels(bullet))
        assertTrue(emptyList<ItemListBlock>().normalizedLevels(bullet).isEmpty())
    }

    @Test
    fun theFirstRowIsAtTheTopAndNoRowIsMoreThanOneDeeperThanTheOneBefore() {
        val damaged = listOf(row("a", 2), row("b", 2), row("c", 0), row("d", 2), row("e", 5))

        assertEquals("a0 b1 c0 d1 e2", damaged.normalizedLevels(bullet).shape())
    }

    @Test
    fun aNegativeLevelIsTheTopAndNothingGoesPastTheDeepestLevel() {
        val rows = listOf(row("a", -3), row("b", 1), row("c", 2), row("d", 3), row("e", 99))

        assertEquals("a0 b1 c2 d2 e2", rows.normalizedLevels(bullet).shape())
    }

    @Test
    fun aKindIsKeptOnlyWhereItDiffersFromTheListAndTheRowIsNested() {
        val rows = listOf(
            row("a", 0, check),
            row("b", 1, bullet),
            row("c", 1, check),
            row("d", 1, BlockType.QUOTE),
        )

        val fixed = rows.normalizedLevels(bullet)

        assertEquals(listOf(null, null, check, null), fixed.map { it.kind })
    }

    @Test
    fun fixingLevelsKeepsTextIdsAndChecks() {
        val rows = listOf(row("a", 4, check = true))

        val fixed = rows.normalizedLevels(bullet).single()

        assertEquals(0, fixed.level)
        assertEquals("a", fixed.value)
        assertEquals(rows.single().id, fixed.id)
        assertTrue(fixed.isCheck)
    }

    // --- subtrees and siblings -------------------------------------------------------------------

    @Test
    fun aSubtreeIsTheRowAndTheDeeperRowsAfterIt() {
        assertEquals(4, tree.subtreeEnd(0))
        assertEquals(3, tree.subtreeEnd(1))
        assertEquals(3, tree.subtreeEnd(2))
        assertEquals(4, tree.subtreeEnd(3))
        assertEquals(5, tree.subtreeEnd(4))
    }

    @Test
    fun siblingsShareAParentAndALevel() {
        assertEquals(listOf(0, 4), tree.siblingStarts(0))
        assertEquals(listOf(0, 4), tree.siblingStarts(4))
        assertEquals(listOf(1, 3), tree.siblingStarts(1))
        assertEquals(listOf(1, 3), tree.siblingStarts(3))
        assertEquals(listOf(2), tree.siblingStarts(2))
    }

    @Test
    fun siblingsDoNotCrossAParent() {
        val rows = listOf(row("a"), row("b", 1), row("c"), row("d", 1), row("e", 1))

        assertEquals(listOf(1), rows.siblingStarts(1))
        assertEquals(listOf(3, 4), rows.siblingStarts(4))
    }

    // --- numbers ---------------------------------------------------------------------------------

    @Test
    fun numbersCountTheRunOfEachLevelAndStartAgainUnderEveryParent() {
        val rows = listOf(row("a"), row("b", 1), row("c", 1), row("d"), row("e", 1), row("f", 2), row("g", 1))

        assertEquals(listOf(1, 1, 2, 2, 1, 1, 2), rows.numbers(number))
    }

    @Test
    fun aDeeperRowDoesNotBreakTheRunOfItsParentsSiblings() {
        val rows = listOf(row("a"), row("b", 1), row("c"), row("d"))

        assertEquals(listOf(1, 1, 2, 3), rows.numbers(number))
    }

    @Test
    fun onlyNumberedRowsGetANumberAndAChangeOfKindStartsTheCountAgain() {
        val rows = listOf(
            row("a"),
            row("b", 1, bullet),
            row("c", 1, number),
            row("d", 1, number),
            row("e", 1, bullet),
            row("f", 1, number),
        )

        assertEquals(listOf(1, 0, 1, 2, 0, 1), rows.numbers(number))
        assertEquals(listOf(0, 0, 1, 2, 0, 1), rows.numbers(bullet))
    }

    // --- shifted ---------------------------------------------------------------------------------

    @Test
    fun aRowGoesOneLevelDeeperWithItsChildren() {
        // b1 c2 d1 under a: indenting d makes it a child of b.
        val moved = tree.shifted(3, deeper = true, listType = bullet)

        assertEquals("a0 b1 c2 d2 e0", moved?.shape())
    }

    @Test
    fun aRowCannotGoDeeperThanTheRowBeforeItPlusOneOrWithoutAnyRowBefore() {
        assertNull(tree.shifted(0, deeper = true, listType = bullet))
        // c is already one deeper than b.
        assertNull(tree.shifted(2, deeper = true, listType = bullet))
        // e at the top follows d at 1: it can go to 1.
        assertEquals("a0 b1 c2 d1 e1", tree.shifted(4, deeper = true, listType = bullet)?.shape())
    }

    @Test
    fun aRowWhoseChildrenWouldPassTheDeepestLevelCannotGoDeeper() {
        // b has a child c at level 2: b cannot go to level 2 with c at 3.
        val rows = listOf(row("a", 0), row("a2", 1), row("b", 1), row("c", 2))

        assertNull(rows.shifted(2, deeper = true, listType = bullet))
        // a2 has no children and can go... it is at 1 after a at 0, which is the most.
        assertNull(rows.shifted(1, deeper = true, listType = bullet))
    }

    @Test
    fun aRowComesOutOneLevelWithItsChildren() {
        assertEquals("a0 b0 c1 d1 e0", tree.shifted(1, deeper = false, listType = bullet)?.shape())
        assertEquals("a0 b1 c1 d1 e0", tree.shifted(2, deeper = false, listType = bullet)?.shape())
    }

    @Test
    fun aRowAtTheTopCannotComeOutAndAMissingRowCannotMove() {
        assertNull(tree.shifted(0, deeper = false, listType = bullet))
        assertNull(tree.shifted(9, deeper = true, listType = bullet))
        assertNull(tree.shifted(-1, deeper = false, listType = bullet))
    }

    @Test
    fun aRowThatComesOutToTheTopLosesItsKind() {
        val rows = listOf(row("a"), row("b", 1, check))

        val out = rows.shifted(1, deeper = false, listType = bullet)

        assertNull(out?.get(1)?.kind)
        assertEquals(0, out?.get(1)?.level)
    }

    // --- movedBy ---------------------------------------------------------------------------------

    @Test
    fun aRowMovesWithItsChildrenPastItsSiblings() {
        val (down, downAt) = assertNotNull(tree.movedBy(0, 1))
        assertEquals("e0 a0 b1 c2 d1", down.shape())
        assertEquals(1, downAt)

        val (up, upAt) = assertNotNull(tree.movedBy(4, -1))
        assertEquals("e0 a0 b1 c2 d1", up.shape())
        assertEquals(0, upAt)
    }

    @Test
    fun aNestedRowMovesAmongItsOwnSiblings() {
        val (down, at) = assertNotNull(tree.movedBy(1, 1))

        assertEquals("a0 d1 b1 c2 e0", down.shape())
        assertEquals(2, at)
        assertEquals(listOf("a", "d", "b", "c", "e"), down.map { it.value })
    }

    @Test
    fun aRowCannotMoveWhereThereIsNoSiblingOrNoMove() {
        assertNull(tree.movedBy(0, -1))
        assertNull(tree.movedBy(4, 1))
        assertNull(tree.movedBy(2, 1))
        assertNull(tree.movedBy(1, 0))
        assertNull(tree.movedBy(9, 1))
        assertNull(tree.movedBy(-1, 1))
        assertNull(tree.movedBy(0, 5))
    }

    @Test
    fun aRowMovesSeveralPlaces() {
        val flat = listOf(row("a"), row("b"), row("c"), row("d"))

        assertEquals("b0 c0 d0 a0", assertNotNull(flat.movedBy(0, 3)).first.shape())
        assertEquals("d0 a0 b0 c0", assertNotNull(flat.movedBy(3, -3)).first.shape())
    }

    // --- siblingOffsetAt -------------------------------------------------------------------------

    @Test
    fun thePlacesAPointerIsOverCountSiblingsNotRows() {
        // Rows are 10 high: a 0-10, b 10-20, c 20-30, d 30-40, e 40-50.
        val tops = listOf(0f, 10f, 20f, 30f, 40f)
        val bottoms = listOf(10f, 20f, 30f, 40f, 50f)

        // a (with its children, down to y 40) dragged to the row of e.
        assertEquals(1, tree.siblingOffsetAt(0, tops, bottoms, 45f))
        // Over its own children it has not moved.
        assertEquals(0, tree.siblingOffsetAt(0, tops, bottoms, 25f))
        // e dragged up over a's subtree.
        assertEquals(-1, tree.siblingOffsetAt(4, tops, bottoms, 15f))
        // d dragged over b's row, or over c, a child of b.
        assertEquals(-1, tree.siblingOffsetAt(3, tops, bottoms, 12f))
        assertEquals(-1, tree.siblingOffsetAt(3, tops, bottoms, 25f))
    }

    @Test
    fun aPointerPastEitherEndOfTheSiblingsIsOverTheNearestEnd() {
        val tops = listOf(0f, 10f, 20f, 30f, 40f)
        val bottoms = listOf(10f, 20f, 30f, 40f, 50f)

        assertEquals(1, tree.siblingOffsetAt(0, tops, bottoms, 500f))
        assertEquals(-1, tree.siblingOffsetAt(4, tops, bottoms, -500f))
    }

    @Test
    fun aRowWithoutKnownBoundsIsNotMoved() {
        assertEquals(0, tree.siblingOffsetAt(0, emptyList(), emptyList(), 5f))
        assertEquals(0, tree.siblingOffsetAt(0, listOf(0f, 10f, 20f, 30f, 40f), emptyList(), 5f))
    }

    // --- withSiblingsOfKind ----------------------------------------------------------------------

    @Test
    fun aKindIsSetOnARowAndItsSiblings() {
        val changed = tree.withSiblingsOfKind(1, check, bullet)

        assertEquals(listOf(null, check, null, check, null), changed?.map { it.kind })
    }

    @Test
    fun aKindThatChangesNothingOrAtTheTopChangesNothing() {
        assertNull(tree.withSiblingsOfKind(0, check, bullet))
        assertNull(tree.withSiblingsOfKind(1, bullet, bullet))
        assertNull(tree.withSiblingsOfKind(9, check, bullet))
    }

    @Test
    fun aKindEqualToTheListsOwnIsStoredAsNone() {
        val checked = tree.withSiblingsOfKind(1, check, bullet) ?: error("changed")

        val back = checked.withSiblingsOfKind(1, bullet, bullet)

        assertEquals(listOf(null, null, null, null, null), back?.map { it.kind })
    }

    @Test
    fun alongsideTakesTheLevelAndKindOfTheOtherRow() {
        val other = row("x", 2, check)

        val placed = row("y").alongside(other)

        assertEquals(2, placed.level)
        assertEquals(check, placed.kind)
        assertEquals("y", placed.value)
    }
}
