package com.pasich.encly.editor.state

import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.TableData
import com.pasich.encly.editor.model.TableRow
import com.pasich.encly.editor.ui.sheets.describe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tables and code in the editor's state: moving through cells, the cell menu, undo, find, conversion and ranges. */
class TablesAndCodeStateTest {
    private var now = 0L
    private val editor = BlockEditorState { now }

    private fun table(vararg rows: List<String>, header: Boolean = true) =
        Block.TableBlock(MutableStateFlow(TableData.of(rows.toList(), header)))

    private fun code(value: String) = Block.CodeBlock(MutableStateFlow(value))

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    private fun grid(data: TableData) = data.rows.map { row -> row.cells.map { it.value } }

    private fun idAt(data: TableData, row: Int, column: Int) = data.rows[row].cells[column].id

    private val sample = TableData.of(listOf(listOf("a", "b", "c"), listOf("d", "e", "f")))

    // --- moving through cells ---------------------------------------------------------------------------

    @Test
    fun theCursorStepsAlongReadingOrderAndColumns() {
        val a = idAt(sample, 0, 0)
        val c = idAt(sample, 0, 2)
        val d = idAt(sample, 1, 0)
        val e = idAt(sample, 1, 1)

        assertEquals(idAt(sample, 0, 1), sample.neighborOf(a, CellStep.NEXT))
        assertEquals(d, sample.neighborOf(c, CellStep.NEXT))
        assertEquals(c, sample.neighborOf(d, CellStep.PREVIOUS))
        assertEquals(a, sample.neighborOf(idAt(sample, 0, 1), CellStep.PREVIOUS))
        assertEquals(e, sample.neighborOf(idAt(sample, 0, 1), CellStep.DOWN))
        assertEquals(idAt(sample, 0, 1), sample.neighborOf(e, CellStep.UP))
    }

    @Test
    fun theTablesEdgesAndUnknownCellsHaveNoNeighbor() {
        assertNull(sample.neighborOf(idAt(sample, 0, 0), CellStep.PREVIOUS))
        assertNull(sample.neighborOf(idAt(sample, 1, 2), CellStep.NEXT))
        assertNull(sample.neighborOf(idAt(sample, 0, 1), CellStep.UP))
        assertNull(sample.neighborOf(idAt(sample, 1, 1), CellStep.DOWN))
        assertNull(sample.neighborOf("nope", CellStep.NEXT))
    }

    @Test
    fun enterGoesDownAndAddsARowAtTheEnd() {
        val inFirst = sample.enterFrom(idAt(sample, 0, 1))!!
        assertEquals(idAt(sample, 1, 1), inFirst.focusCellId)
        assertEquals(grid(sample), grid(inFirst.table))

        val inLast = sample.enterFrom(idAt(sample, 1, 1))!!
        assertEquals(3, inLast.table.rowCount)
        assertEquals(idAt(inLast.table, 2, 1), inLast.focusCellId)
        assertNull(sample.enterFrom("nope"))
    }

    @Test
    fun tabGoesOnAndAddsARowAfterTheLastCell() {
        val next = sample.tabFrom(idAt(sample, 0, 0))!!
        assertEquals(idAt(sample, 0, 1), next.focusCellId)

        val last = sample.tabFrom(idAt(sample, 1, 2))!!
        assertEquals(3, last.table.rowCount)
        assertEquals(idAt(last.table, 2, 0), last.focusCellId)
        assertNull(sample.tabFrom("nope"))
    }

    // --- the cell menu ---------------------------------------------------------------------------------

    @Test
    fun aRowAndAColumnAreAddedAfterTheCellsOwn() {
        val row = sample.addRowBelow(idAt(sample, 0, 2))!!
        assertEquals(listOf(listOf("a", "b", "c"), listOf("", "", ""), listOf("d", "e", "f")), grid(row.table))
        assertEquals(idAt(row.table, 1, 2), row.focusCellId)

        val column = sample.addColumnAfter(idAt(sample, 1, 0))!!
        assertEquals(listOf(listOf("a", "", "b", "c"), listOf("d", "", "e", "f")), grid(column.table))
        assertEquals(idAt(column.table, 1, 1), column.focusCellId)
        assertNull(sample.addRowBelow("nope"))
        assertNull(sample.addColumnAfter("nope"))
    }

    @Test
    fun aRowAndAColumnAreDeletedWithTheCursorLeftInTheNeighbor() {
        val row = sample.removeRowOf(idAt(sample, 0, 1))!!
        assertEquals(listOf(listOf("d", "e", "f")), grid(row.table))
        assertEquals(idAt(row.table, 0, 1), row.focusCellId)
        val lastRow = sample.removeRowOf(idAt(sample, 1, 2))!!
        assertEquals(idAt(lastRow.table, 0, 2), lastRow.focusCellId)

        val column = sample.removeColumnOf(idAt(sample, 0, 0))!!
        assertEquals(listOf(listOf("b", "c"), listOf("e", "f")), grid(column.table))
        assertEquals(idAt(column.table, 0, 0), column.focusCellId)
        val lastColumn = sample.removeColumnOf(idAt(sample, 1, 2))!!
        assertEquals(idAt(lastColumn.table, 1, 1), lastColumn.focusCellId)
    }

    @Test
    fun theOnlyRowAndColumnCannotBeDeleted() {
        val single = TableData.of(listOf(listOf("x")))

        assertNull(single.removeRowOf(idAt(single, 0, 0)))
        assertNull(single.removeColumnOf(idAt(single, 0, 0)))
        assertNull(sample.removeRowOf("nope"))
        assertNull(sample.removeColumnOf("nope"))
    }

    @Test
    fun theHeaderSwitchLeavesTheCursorAlone() {
        val off = sample.toggleHeader()
        assertFalse(off.table.header)
        assertNull(off.focusCellId)
        assertTrue(off.table.toggleHeader().table.header)
    }

    // --- typing steps and the cursor after undo -----------------------------------------------------------

    @Test
    fun typingInOneCellIsATypingStepAndNothingElseIs() {
        val a = idAt(sample, 0, 0)
        val typed = sample.withCell(a, "ax")

        val step = tableTypingStep(sample, typed)
        assertNotNull(step)
        assertEquals(a, step.field)
        assertEquals(TypingKind.INSERT, step.kind)

        assertNull(tableTypingStep(sample, sample.addRow(1)))
        assertNull(tableTypingStep(sample, sample.addColumn(1)))
        assertNull(tableTypingStep(sample, sample.withHeader(false)))
        assertNull(tableTypingStep(sample, sample.withCell(a, "x").withCell(idAt(sample, 1, 1), "y")))
        assertNull(tableTypingStep(sample, sample))
        // A cell swapped for another (same shape, new id) is not typing.
        assertNull(tableTypingStep(sample, sample.duplicate()))
        val first = sample.rows[0]
        val swapped = sample.copy(
            rows =
            listOf(TableRow(listOf(first.cells[0].copy(id = "other")) + first.cells.drop(1))) + sample.rows.drop(1),
        )
        assertNull(tableTypingStep(sample, swapped))
    }

    @Test
    fun cellTextIsOneLine() {
        assertEquals("a b c d", cellText("a\nb\r\nc \n\nd"))
        assertEquals("plain", cellText("plain"))
    }

    @Test
    fun undoAndRedoBringBackTheTableAndPutTheCursorInTheCellThatChanged() = runTest {
        val block = table(listOf("hello", "b"))
        editor.load(listOf(block))
        editor.selection.onFocused(block.id)
        val cell = idAt(block.table.value, 0, 0)

        now += BlockOperations.MERGE_WINDOW_MS + 1
        val typed = block.table.value.withCell(cell, "hello!")
        editor.changeValue(
            block.id,
            block.table,
            typed,
            mergeable = true,
            step = tableTypingStep(block.table.value, typed),
        )

        editor.undo()
        val undone = editor.selection.requests.first()
        assertEquals("hello", block.table.value.valueAt(0, 0))
        assertEquals(cell, undone.itemId)
        assertEquals(5, undone.caret)

        editor.redo()
        val redone = editor.selection.requests.first()
        assertEquals("hello!", block.table.value.valueAt(0, 0))
        assertEquals(cell, redone.itemId)
        assertEquals(6, redone.caret)
    }

    @Test
    fun aRowAddedIsOneUndoStepAndTheCursorStaysInTheTable() = runTest {
        val block = table(listOf("a"))
        editor.load(listOf(block))
        editor.selection.onFocused(block.id)

        editor.changeValue(block.id, block.table, block.table.value.addRow(1), mergeable = false)
        assertEquals(2, block.table.value.rowCount)

        editor.undo()
        assertEquals(1, block.table.value.rowCount)
        assertEquals(block.id, editor.selection.requests.first().blockId)
        editor.redo()
        assertEquals(2, block.table.value.rowCount)
    }

    @Test
    fun aCaretOfATableThatDidNotChangeTextIsNone() {
        assertNull(tableCaret("b", sample, sample))
        assertNull(tableCaret("b", sample.addRow(1), sample.addRow(1)))
        val typed = sample.withCell(idAt(sample, 1, 2), "fx")
        assertEquals(CaretTarget("b", idAt(sample, 1, 2), 2), tableCaret("b", sample, typed))
        // A cell that was not there before has no earlier text to compare.
        assertNull(tableCaret("b", sample.removeRow(1), sample))
    }

    @Test
    fun caretTargetOfKnowsTablesAndStillIgnoresOtherValues() {
        val typed = sample.withCell(idAt(sample, 0, 0), "ab")
        assertEquals(1, caretTargetOf("b", typed, sample)?.caret)
        assertNull(caretTargetOf("b", sample, 5))
    }

    // --- code blocks ---------------------------------------------------------------------------------------

    @Test
    fun enterAndPastesNeverSplitCode() {
        val block = code("fun a() {")
        editor.load(listOf(block))

        assertNull(editor.breakLine(block.id, LineBreak("fun a() {", listOf("", ""), "")))
        assertNull(editor.breakLine(block.id, LineBreak("x", listOf("a", "b"), "")))
        assertEquals(1, editor.blocks.size)
    }

    @Test
    fun backspaceAtTheStartOfCodeMakesItAParagraphWithTheSameText() {
        val block = code("  indented")
        editor.load(listOf(block))

        assertTrue(editor.backspaceAtStart(block.id))

        val paragraph = editor.blocks.single() as Block.TextBlock
        assertEquals("  indented", paragraph.text.value)
    }

    @Test
    fun aParagraphAfterCodeJoinsItAndOneAfterATableDoesNot() {
        val first = code("a")
        val second = text("b")
        editor.load(listOf(first, second))
        assertTrue(editor.backspaceAtStart(second.id))
        assertEquals("ab", first.text.value)
        assertEquals(1, editor.blocks.size)

        val grid = table(listOf("x"))
        val below = text("y")
        editor.load(listOf(grid, below))
        assertFalse(editor.backspaceAtStart(below.id))
        assertEquals(2, editor.blocks.size)
    }

    @Test
    fun theCodeToolTurnsTextIntoCodeAndBackAndMakesCodeOfAList() {
        val paragraph = text("val x = 1")
        editor.load(listOf(paragraph))
        editor.selection.onFocused(paragraph.id)

        editor.applyTool(BlockType.CODE)
        assertEquals("val x = 1", (editor.blocks.single() as Block.CodeBlock).text.value)
        editor.applyTool(BlockType.CODE)
        assertEquals("val x = 1", (editor.blocks.single() as Block.TextBlock).text.value)

        val list = Block.ListBlock(
            MutableStateFlow(listOf(ItemListBlock("a"), ItemListBlock("b"))),
            BlockType.LIST_BULLET,
        )
        editor.load(listOf(list))
        editor.selection.onFocused(list.id)
        editor.applyTool(BlockType.CODE)
        assertEquals("a\nb", (editor.blocks.single() as Block.CodeBlock).text.value)
    }

    @Test
    fun theTableToolAddsATableAfterTheBlockInsteadOfConvertingIt() {
        val paragraph = text("keep me")
        editor.load(listOf(paragraph))
        editor.selection.onFocused(paragraph.id)

        editor.applyTool(BlockType.TABLE)

        assertEquals(2, editor.blocks.size)
        assertEquals("keep me", (editor.blocks[0] as Block.TextBlock).text.value)
        assertTrue(editor.blocks[1] is Block.TableBlock)
        assertEquals(BlockType.TABLE, editor.blocks[1].toolType())
        assertEquals(BlockType.CODE, code("x").toolType())
    }

    @Test
    fun theBlockSheetsNameCodeAndTables() {
        assertEquals(EditorString.BLOCK_CODE, code("x").describe())
        assertEquals(EditorString.BLOCK_TABLE, table(listOf("x")).describe())
    }

    @Test
    fun theSlashPickerOffersCodeAndTables() {
        val paragraph = text("/")
        editor.load(listOf(paragraph))
        assertTrue(editor.applySlash(paragraph.id, SlashCommand.CODE))
        assertTrue(editor.blocks.single() is Block.CodeBlock)

        val another = text("/")
        editor.load(listOf(another))
        assertTrue(editor.applySlash(another.id, SlashCommand.TABLE))
        assertTrue(editor.blocks.single() is Block.TableBlock)

        val labels = SlashCommand.entries.associateWith { it.name.lowercase() }
        assertEquals(listOf(SlashCommand.CODE), slashMatches("snip", labels))
        assertEquals(listOf(SlashCommand.TABLE), slashMatches("grid", labels))
    }

    @Test
    fun aMarkIsNeverMadeOfCodeOrATable() {
        val made = shortcutBlock(Shortcut(BlockType.CODE, "x"), "rest")
        assertEquals("rest", (made as Block.TextBlock).text.value)
        assertTrue(shortcutBlock(Shortcut(BlockType.TABLE, "x"), "") is Block.TextBlock)
    }

    // --- every kind of editing that meets them ----------------------------------------------------------------

    @Test
    fun bothTakeFocusAndDuplicateWithoutSharingAnything() {
        val grid = table(listOf("a", "b"))
        val snippet = code("x")
        editor.load(listOf(grid, snippet))

        assertTrue(grid.canTakeFocus())
        assertTrue(snippet.canTakeFocus())
        assertTrue(editor.duplicateBlockOf(grid.id))
        assertTrue(editor.duplicateBlockOf(snippet.id))

        val copy = editor.blocks[1] as Block.TableBlock
        assertEquals(grid(grid.table.value), grid(copy.table.value))
        assertTrue(copy.table.value.rows[0].cells[0].id != grid.table.value.rows[0].cells[0].id)
        copy.table.value = copy.table.value.withCell(idAt(copy.table.value, 0, 0), "changed")
        assertEquals("a", grid.table.value.valueAt(0, 0))
        val codeCopy = editor.blocks[3] as Block.CodeBlock
        codeCopy.text.value = "y"
        assertEquals("x", snippet.text.value)
    }

    @Test
    fun findSearchesCodeAndEveryCellOfATable() {
        val grid = table(listOf("Alpha", "beta"), listOf("gamma", "alpha again"))
        val snippet = code("val alpha = 1")
        editor.load(listOf(grid, snippet))

        val matches = findMatches(editor.blocks, "alpha")

        assertEquals(3, matches.size)
        assertEquals(idAt(grid.table.value, 0, 0), matches[0].itemId)
        assertEquals(idAt(grid.table.value, 1, 1), matches[1].itemId)
        assertEquals(snippet.id to null, matches[2].blockId to matches[2].itemId)
    }

    @Test
    fun selectedBlocksAreCopiedAsPlainTextWithATableAsRowsOfCells() {
        val grid = table(listOf("a", "b"), listOf("c", "d"))
        val snippet = code("x = 1\n  y = 2")
        editor.load(listOf(grid, snippet))

        assertEquals("a | b\nc | d\n\nx = 1\n  y = 2", editor.blocks.plainText())
    }

    @Test
    fun aRangeThatReachesATableTakesTheWholeTableWhetherItsEndIsInOrAround() {
        val before = text("before")
        val grid = table(listOf("a", "b"), listOf("c", "d"))
        val after = text("after")

        // Across: from the middle of the paragraph above to the middle of the one below.
        editor.load(listOf(before, grid, after))
        editor.textRange.select(TextPoint(before.id, 3), TextPoint(after.id, 2))
        assertTrue(editor.textRangeBlocks().any { it is Block.TableBlock })
        assertTrue(editor.replaceTextRange(""))
        assertEquals(listOf("befter"), editor.blocks.map { (it as Block.TextBlock).text.value })

        // An end inside a cell: the table goes whole, the text on the other side is cut as usual.
        val second = table(listOf("a", "b"))
        val tail = text("tail text")
        editor.load(listOf(second, tail))
        editor.textRange.select(TextPoint(second.id, 1, idAt(second.table.value, 0, 1)), TextPoint(tail.id, 5))
        assertEquals(1, editor.textRangeBlocks().count { it is Block.TableBlock })
        assertTrue(editor.replaceTextRange(""))
        assertEquals(listOf("text"), editor.blocks.map { (it as Block.TextBlock).text.value })

        val first = text("head text")
        val third = table(listOf("a"))
        editor.load(listOf(first, third))
        editor.textRange.select(TextPoint(first.id, 5), TextPoint(third.id, 0, idAt(third.table.value, 0, 0)))
        assertTrue(editor.replaceTextRange(""))
        assertEquals(listOf("head "), editor.blocks.map { (it as Block.TextBlock).text.value })
    }

    @Test
    fun aRangeCrossingACodeBlockKeepsTheFirstBlocksKind() {
        val first = code("one two")
        val last = text("three four")
        editor.load(listOf(first, last))
        editor.textRange.select(TextPoint(first.id, 4), TextPoint(last.id, 6))

        assertTrue(editor.replaceTextRange(""))

        assertEquals("one four", (editor.blocks.single() as Block.CodeBlock).text.value)
    }

    @Test
    fun theDocumentCursorMovesPastTablesAndCodeLikeAnyOtherField() {
        val snippet = code("ab")
        val grid = table(listOf("x"))
        val last = text("cd")
        editor.load(listOf(snippet, grid, last))
        editor.textRange.select(TextPoint(snippet.id, 1), TextPoint(snippet.id, 1))

        editor.textRange.moveHead(HeadMove.DOWN)

        assertEquals(last.id, editor.textRange.ends.value?.head?.blockId)
    }
}
