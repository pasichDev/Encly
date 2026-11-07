package com.pasich.encly.editor.ui.blocks

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import com.pasich.encly.editor.model.ItemListBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The keys, the per-item possibilities and the drag arithmetic behind reordering and nesting list items. */
class ListReorderTest {
    private fun row(text: String, level: Int = 0) = ItemListBlock(text, level = level)

    private fun key(
        key: Key,
        type: KeyEventType = KeyEventType.KeyDown,
        command: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
    ) = listItemKeyOf(key, type, command, alt, shift)

    // a0 b1 c2 d1 e0
    private val rows = listOf(row("a"), row("b", 1), row("c", 2), row("d", 1), row("e"))

    // --- keys -------------------------------------------------------------------------------------

    @Test
    fun tabIndentsAndShiftTabOutdents() {
        assertEquals(ListItemKey.INDENT, key(Key.Tab))
        assertEquals(ListItemKey.OUTDENT, key(Key.Tab, shift = true))
    }

    @Test
    fun altShiftUpAndDownMoveTheItem() {
        assertEquals(ListItemKey.MOVE_UP, key(Key.DirectionUp, alt = true, shift = true))
        assertEquals(ListItemKey.MOVE_DOWN, key(Key.DirectionDown, alt = true, shift = true))
    }

    @Test
    fun theBlockMoveChordAndPlainArrowsAreNotListItemKeys() {
        // Ctrl or Cmd+Shift+Arrow moves the whole block; with Alt as well it is neither.
        assertNull(key(Key.DirectionUp, command = true, shift = true))
        assertNull(key(Key.DirectionDown, command = true, shift = true))
        assertNull(key(Key.DirectionUp, command = true, alt = true, shift = true))
        assertNull(key(Key.DirectionUp, command = true, alt = true, shift = true))
        assertNull(key(Key.DirectionUp))
        assertNull(key(Key.DirectionDown, shift = true))
        assertNull(key(Key.DirectionUp, alt = true))
        assertNull(key(Key.DirectionDown, alt = true))
    }

    @Test
    fun otherKeysAndKeysComingUpAreNotListItemKeys() {
        assertNull(key(Key.Tab, type = KeyEventType.KeyUp))
        assertNull(key(Key.Tab, command = true))
        assertNull(key(Key.Tab, command = true, alt = true))
        assertNull(key(Key.Tab, alt = true))
        assertNull(key(Key.A))
        assertNull(key(Key.DirectionUp, type = KeyEventType.KeyUp, alt = true, shift = true))
    }

    // --- what each item can do --------------------------------------------------------------------

    @Test
    fun anItemCanMoveWhereItHasASiblingAndNestWhereTheItemAboveAllowsIt() {
        val moves = movesOf(rows)

        // a: first of its siblings, e is the other; nothing above to nest under, nothing to come out of.
        assertEquals(listOf(false, true, false, false), moves[0].let { listOf(it.up, it.down, it.indent, it.outdent) })
        // b: first child of a, d is its next sibling; it is one deeper than a already.
        assertEquals(listOf(false, true, false, true), moves[1].let { listOf(it.up, it.down, it.indent, it.outdent) })
        // c: the only child of b; at the deepest level.
        assertEquals(listOf(false, false, false, true), moves[2].let { listOf(it.up, it.down, it.indent, it.outdent) })
        // d: after b, and it can nest under c? c is at 2, d at 1: it can go to 2.
        assertEquals(listOf(true, false, true, true), moves[3].let { listOf(it.up, it.down, it.indent, it.outdent) })
        // e: after a, and it can nest under d.
        assertEquals(listOf(true, false, true, false), moves[4].let { listOf(it.up, it.down, it.indent, it.outdent) })
    }

    @Test
    fun anItemWhoseChildrenAreAtTheDeepestLevelCannotNestFurther() {
        val moves = movesOf(listOf(row("a"), row("b", 1), row("c", 2), row("d", 1), row("e", 2)))

        // d with child e: d could go under b's child c, but e would pass the deepest level.
        assertFalse(moves[3].indent)
        assertTrue(moves[3].outdent)
    }

    @Test
    fun aListOfOneItemCanDoNothingButOutdentIfNested() {
        val moves = movesOf(listOf(row("only")))

        assertFalse(moves.single().up || moves.single().down || moves.single().indent || moves.single().outdent)
    }

    // --- drag -------------------------------------------------------------------------------------

    private fun placed(drag: ListDrag, rows: List<ItemListBlock>, height: Float = 10f) {
        rows.forEachIndexed { at, row -> drag.place(row.id, at * height, (at + 1) * height) }
    }

    @Test
    fun aDragMarksTheItemAndItsChildrenAsMovingAndEndsClean() {
        val drag = ListDrag { rows }
        placed(drag, rows)

        drag.begin(rows[1].id)

        assertEquals(rows[1].id, drag.itemId)
        assertEquals(setOf(rows[1].id, rows[2].id), drag.moving)
        drag.by(4f)
        drag.by(3f)
        assertEquals(7f, drag.offset)

        drag.end()

        assertNull(drag.itemId)
        assertTrue(drag.moving.isEmpty())
        assertEquals(0f, drag.offset)
    }

    @Test
    fun aDragOfAnItemThatIsNotThereDoesNotStart() {
        val drag = ListDrag { rows }

        drag.begin("nope")

        assertNull(drag.itemId)
        assertEquals(0, drag.placesMoved())
        assertNull(drag.dropLineAt())
    }

    @Test
    fun anItemTakenDownOverItsNextSiblingHasMovedOnePlaceAndALineShowsWhere() {
        val drag = ListDrag { rows }
        placed(drag, rows)
        drag.begin(rows[0].id)

        // a with b c d is 0-40; dragged 30 down its middle is at 50, over e (40-50).
        drag.by(30f)

        assertEquals(1, drag.placesMoved())
        assertEquals(50f, drag.dropLineAt())
    }

    @Test
    fun anItemTakenUpOverItsPreviousSiblingHasMovedOnePlaceAndALineShowsWhere() {
        val drag = ListDrag { rows }
        placed(drag, rows)
        drag.begin(rows[4].id)

        drag.by(-30f)

        assertEquals(-1, drag.placesMoved())
        assertEquals(0f, drag.dropLineAt())
    }

    @Test
    fun anItemThatHasNotLeftItsOwnRowsHasNotMoved() {
        val drag = ListDrag { rows }
        placed(drag, rows)
        drag.begin(rows[3].id)

        drag.by(2f)

        assertEquals(0, drag.placesMoved())
        assertNull(drag.dropLineAt())
    }

    @Test
    fun anItemWithoutKnownPositionsHasNotMoved() {
        val drag = ListDrag { rows }
        drag.begin(rows[0].id)
        drag.by(100f)

        assertEquals(0, drag.placesMoved())
    }

    @Test
    fun anItemWhoseLastChildHasNoKnownPositionHasNotMoved() {
        val drag = ListDrag { rows }
        drag.place(rows[0].id, 0f, 10f)
        drag.begin(rows[0].id)
        drag.by(100f)

        assertEquals(0, drag.placesMoved())
    }
}
