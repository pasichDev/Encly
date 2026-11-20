package com.pasich.encly.editor.state

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.TableData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A locked checklist is out of reach of every edit that crosses blocks, and a selection that ends inside a table
 * leaves the table alone unless it covers it whole.
 */
class EditorFeatureFixesTest {
    private val editor = BlockEditorState { 0L }

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    private fun checklist(vararg items: ItemListBlock, locked: Boolean = true) =
        Block.ListBlock(MutableStateFlow(items.toList()), BlockType.LIST_CHECK, locked = MutableStateFlow(locked))

    private fun grid(vararg rows: List<String>) = Block.TableBlock(MutableStateFlow(TableData.of(rows.toList())))

    private fun cell(block: Block.TableBlock, row: Int, column: Int) = block.table.value.rows[row].cells[column].id

    private fun texts() = editor.blocks.map {
        when (it) {
            is Block.TextBlock -> it.text.value
            is Block.TableBlock -> "table"
            is Block.ListBlock -> it.items.value.joinToString("|") { item -> item.value }
            else -> it::class.simpleName.orEmpty()
        }
    }

    // --- a locked checklist ------------------------------------------------------------------------------

    @Test
    fun aSelectionOfTheWholeNoteChangesNothingWhileAChecklistIsLocked() {
        val list = checklist(ItemListBlock("one"), ItemListBlock("two"))
        editor.load(listOf(list, text("tail")))
        editor.textRange.selectAll()

        assertFalse(editor.canReplaceTextRange())
        assertFalse(editor.replaceTextRange("x"))
        assertEquals(listOf("one|two", "tail"), texts())
        assertTrue(editor.textRange.isActive)
    }

    @Test
    fun aLockedChecklistInTheMiddleOrAtTheEndOfARangeIsNotChangedEither() {
        val first = text("start")
        val list = checklist(ItemListBlock("one"), ItemListBlock("two"))
        val last = text("end")
        editor.load(listOf(first, list, last))

        editor.textRange.select(TextPoint(first.id, 2), TextPoint(last.id, 1))
        assertFalse(editor.replaceTextRange(""))

        editor.textRange.select(TextPoint(first.id, 2), TextPoint(list.id, 1, list.items.value[1].id))
        assertFalse(editor.replaceTextRange(""))
        assertEquals(listOf("start", "one|two", "end"), texts())
    }

    @Test
    fun unlockedTheSameSelectionIsReplaced() {
        val first = text("start")
        val list = checklist(ItemListBlock("one"), ItemListBlock("two"), locked = false)
        editor.load(listOf(first, list))
        editor.textRange.select(TextPoint(first.id, 2), TextPoint(list.id, 1, list.items.value[1].id))

        assertTrue(editor.canReplaceTextRange())
        assertTrue(editor.replaceTextRange(""))
    }

    @Test
    fun withoutASelectionThereIsNothingToReplace() {
        editor.load(listOf(text("a")))

        assertFalse(editor.canReplaceTextRange())
        assertFalse(editor.replaceTextRange("x"))
    }

    @Test
    fun aLockedChecklistCannotBeIndentedMovedOrChangedToAnotherKind() {
        val items = listOf(ItemListBlock("one"), ItemListBlock("two"), ItemListBlock("three", level = 1))
        val list = checklist(*items.toTypedArray())
        editor.load(listOf(list))
        val two = list.items.value[1].id
        val three = list.items.value[2].id

        assertFalse(editor.canShiftListItem(list.id, two, deeper = true))
        assertFalse(editor.shiftListItem(list.id, two, deeper = true))
        assertFalse(editor.canMoveListItem(list.id, two, -1))
        assertFalse(editor.moveListItem(list.id, two, -1))
        assertFalse(editor.setNestedListKind(list.id, three, BlockType.LIST_BULLET))
        assertEquals(items.map { it.copy(id = "") }, list.items.value.map { it.copy(id = "") })

        list.locked.value = false
        assertTrue(editor.canShiftListItem(list.id, two, deeper = true))
        assertTrue(editor.moveListItem(list.id, two, -1))
        assertTrue(editor.shiftListItem(list.id, three, deeper = false))
    }

    // --- a selection that ends in a table ----------------------------------------------------------------

    @Test
    fun aDragFromACellIntoTheParagraphBelowReplacesOnlyWhatIsOutsideTheTable() {
        val table = grid(listOf("a", "b"), listOf("c", "d"))
        val below = text("below text")
        editor.load(listOf(table, below))
        editor.textRange.select(TextPoint(table.id, 1, cell(table, 1, 0)), TextPoint(below.id, 6))

        assertTrue(editor.textRange.isCrossField)
        assertTrue(editor.replaceTextRange("x"))
        assertEquals(listOf("table", "xtext"), texts())
    }

    @Test
    fun aDragFromAParagraphIntoACellStopsAtTheTable() {
        val above = text("above text")
        val table = grid(listOf("a", "b"), listOf("c", "d"))
        editor.load(listOf(above, table))
        editor.textRange.select(TextPoint(above.id, 6), TextPoint(table.id, 1, cell(table, 1, 0)))

        assertTrue(editor.textRange.isCrossField)
        assertEquals("text", editor.textRangeBlocks().single().let { (it as Block.TextBlock).text.value })
        assertTrue(editor.replaceTextRange(""))
        assertEquals(listOf("above ", "table"), texts())
    }

    @Test
    fun aTableIsTakenWholeWhenTheSelectionCoversItFromItsFirstCellOrToItsLast() {
        // To the very end of the last cell.
        val above = text("above")
        val table = grid(listOf("a", "b"), listOf("c", "dd"))
        editor.load(listOf(above, table, text("below")))
        editor.textRange.select(TextPoint(above.id, 2), TextPoint(table.id, 2, cell(table, 1, 1)))
        assertTrue(editor.replaceTextRange(""))
        assertEquals(listOf("ab", "below"), texts())

        // From the start of the first cell, named or not.
        val named = grid(listOf("a", "b"))
        val after = text("after")
        editor.load(listOf(named, after))
        editor.textRange.select(TextPoint(named.id, 0, cell(named, 0, 0)), TextPoint(after.id, 2))
        assertTrue(editor.replaceTextRange(""))
        assertEquals(listOf("ter"), texts())

        val unnamed = grid(listOf("a", "b"))
        val last = text("last")
        editor.load(listOf(unnamed, last))
        editor.textRange.select(TextPoint(unnamed.id, 0), TextPoint(last.id, 2))
        assertTrue(editor.replaceTextRange(""))
        assertEquals(listOf("st"), texts())

        // Select all covers a table that is the whole note.
        val only = grid(listOf("a", "bb"))
        editor.load(listOf(only))
        editor.textRange.selectAll()
        assertTrue(editor.replaceTextRange(""))
        assertEquals(listOf(""), texts())
    }

    private class Scene(val above: Block.TextBlock, val table: Block.TableBlock, val below: Block.TextBlock)

    // A note of a paragraph, a table and a paragraph, replaced from [from] to [to] with nothing.
    private fun cut(from: (Scene) -> TextPoint, to: (Scene) -> TextPoint): List<String> {
        val scene = Scene(text("above"), grid(listOf("a", "bb"), listOf("c", "dd")), text("below"))
        editor.load(listOf(scene.above, scene.table, scene.below))
        editor.textRange.select(from(scene), to(scene))
        assertTrue(editor.replaceTextRange(""))
        return texts()
    }

    @Test
    fun aTableCoveredOnlyInPartIsLeftOutWhicheverEdgeOrCellThePointIsIn() {
        // The end is in the last cell but not at its end, or in another cell.
        val kept = listOf("ab", "table", "below")
        assertEquals(
            kept,
            cut({ TextPoint(it.above.id, 2) }, { TextPoint(it.table.id, 1, cell(it.table, 1, 1)) }),
        )
        assertEquals(
            kept,
            cut({ TextPoint(it.above.id, 2) }, { TextPoint(it.table.id, 2, cell(it.table, 1, 0)) }),
        )

        // The start is not at the start of the first cell, or is in another cell.
        val rest = listOf("above", "table", "low")
        assertEquals(
            rest,
            cut({ TextPoint(it.table.id, 1, cell(it.table, 0, 0)) }, { TextPoint(it.below.id, 2) }),
        )
        assertEquals(
            rest,
            cut({ TextPoint(it.table.id, 0, cell(it.table, 0, 1)) }, { TextPoint(it.below.id, 2) }),
        )
    }

    @Test
    fun aSelectionBetweenTwoTablesIsLeftWithWhatTheyCover() {
        val table = grid(listOf("a", "b"))
        val other = grid(listOf("c", "d"))
        editor.load(listOf(table, other))

        // Both ends in the middle of tables: nothing is left of the selection, which still counts as across fields.
        editor.textRange.select(TextPoint(table.id, 1, cell(table, 0, 0)), TextPoint(other.id, 0, cell(other, 0, 1)))
        assertTrue(editor.textRange.isCrossField)
        assertFalse(editor.canReplaceTextRange())
        assertFalse(editor.replaceTextRange("x"))

        // The first table is covered in part, the second to its end: the second goes, the first stays.
        editor.textRange.select(TextPoint(table.id, 1, cell(table, 0, 0)), TextPoint(other.id, 1, cell(other, 0, 1)))
        assertTrue(editor.replaceTextRange(""))
        assertEquals(listOf("table", ""), texts())
    }

    // --- the cursor goes back to the cell it was in ------------------------------------------------------

    @Test
    fun theKeyboardComesBackToTheCellTheCursorWasIn() {
        val table = grid(listOf("ab", "cd"), listOf("ef", "gh"))
        editor.load(listOf(table))
        var request: FocusRequest? = null
        editor.selection.immediateFocus = { request = it }

        editor.selection.onItemCaret(table.id, cell(table, 1, 0), 1)
        editor.focusWorkingBlock()
        assertEquals(cell(table, 1, 0), request?.itemId)
        assertEquals(1, request?.caret)

        // A cell that has gone is not asked for.
        table.table.value = table.table.value.removeRow(1)
        editor.focusWorkingBlock()
        assertNull(request?.itemId)
    }

    @Test
    fun theKeyboardComesBackToATableThatNeverHadACursorWithoutACell() {
        val table = grid(listOf("ab", "cd"))
        editor.load(listOf(table))
        var request: FocusRequest? = null
        editor.selection.immediateFocus = { request = it }

        editor.focusWorkingBlock()

        assertNull(request?.itemId)
        assertTrue(request?.cursorToEnd == true)
    }

    @Test
    fun theKeyboardComesBackToAParagraphWithoutAnyCell() {
        val paragraph = text("abc")
        editor.load(listOf(paragraph))
        var request: FocusRequest? = null
        editor.selection.immediateFocus = { request = it }

        editor.focusWorkingBlock()

        assertNull(request?.itemId)
    }
}
