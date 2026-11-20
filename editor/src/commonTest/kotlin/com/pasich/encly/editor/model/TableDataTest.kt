package com.pasich.encly.editor.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TableDataTest {
    private fun table(vararg rows: List<String>, header: Boolean = true) = TableData.of(rows.toList(), header)

    private fun grid(data: TableData) = data.rows.map { row -> row.cells.map { it.value } }

    @Test
    fun aNewTableIsTwoByTwoWithAHeaderAndNeverSmallerThanOneByOne() {
        val data = TableData.empty()
        assertEquals(2, data.rowCount)
        assertEquals(2, data.columnCount)
        assertTrue(data.header)
        assertEquals(listOf(listOf("")), grid(TableData.empty(rows = 0, columns = -3)))
    }

    @Test
    fun aGridThatIsEmptyOrRaggedIsRefusedAtConstruction() {
        assertFailsWith<IllegalArgumentException> { TableData(emptyList()) }
        assertFailsWith<IllegalArgumentException> { TableData(listOf(TableRow(emptyList()))) }
        assertFailsWith<IllegalArgumentException> {
            TableData(listOf(TableRow(listOf(TableCell("a"), TableCell("b"))), TableRow(listOf(TableCell("c")))))
        }
        assertEquals(1, TableData(listOf(TableRow(listOf(TableCell("a"))))).columnCount)
    }

    @Test
    fun ofPadsShortRowsAndMakesAnEmptyGridOneCell() {
        assertEquals(
            listOf(listOf("a", "b", "c"), listOf("d", "", "")),
            grid(table(listOf("a", "b", "c"), listOf("d"))),
        )
        assertEquals(listOf(listOf("")), grid(TableData.of(emptyList())))
        assertEquals(listOf(listOf("")), grid(TableData.of(listOf(emptyList()))))
        assertFalse(TableData.of(listOf(listOf("a")), header = false).header)
    }

    @Test
    fun valueAtAnswersEmptyOutsideTheTable() {
        val data = table(listOf("a", "b"), listOf("c", "d"))
        assertEquals("d", data.valueAt(1, 1))
        assertEquals("", data.valueAt(2, 0))
        assertEquals("", data.valueAt(0, 2))
    }

    @Test
    fun aCellIsFoundByIdWhereverItSits() {
        val data = table(listOf("a", "b"), listOf("c", "d"))
        val d = data.rows[1].cells[1]

        assertEquals(1 to 1, data.locate(d.id))
        assertEquals(d, data.cell(d.id))
        assertNull(data.locate("nope"))
        assertNull(data.cell("nope"))
    }

    @Test
    fun withCellChangesOnlyThatCellAndKeepsItsId() {
        val data = table(listOf("a", "b"))
        val b = data.rows[0].cells[1]

        val changed = data.withCell(b.id, "B")

        assertEquals(listOf(listOf("a", "B")), grid(changed))
        assertEquals(b.id, changed.rows[0].cells[1].id)
        assertEquals(grid(data), grid(data.withCell("nope", "x")))
    }

    @Test
    fun rowsAreAddedAtAnyPlaceAndClamped() {
        val data = table(listOf("a", "b"), listOf("c", "d"))

        assertEquals(listOf(listOf("", ""), listOf("a", "b"), listOf("c", "d")), grid(data.addRow(0)))
        assertEquals(listOf(listOf("a", "b"), listOf("", ""), listOf("c", "d")), grid(data.addRow(1)))
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d"), listOf("", "")), grid(data.addRow(99)))
        assertEquals(3, data.addRow(-4).rowCount)
        // The new cells never share an id with the old ones.
        assertEquals(6, data.addRow(1).rows.flatMap { it.cells }.map { it.id }.toSet().size)
    }

    @Test
    fun aRowIsRemovedUnlessItIsTheOnlyOneOrIsNotThere() {
        val data = table(listOf("a"), listOf("b"))

        assertEquals(listOf(listOf("b")), grid(data.removeRow(0)))
        assertEquals(listOf(listOf("a")), grid(data.removeRow(1)))
        assertSame(data, data.removeRow(2))
        assertSame(data, data.removeRow(-1))
        val single = table(listOf("a"))
        assertSame(single, single.removeRow(0))
    }

    @Test
    fun columnsAreAddedAndRemovedLikeRows() {
        val data = table(listOf("a", "b"), listOf("c", "d"))

        assertEquals(listOf(listOf("", "a", "b"), listOf("", "c", "d")), grid(data.addColumn(0)))
        assertEquals(listOf(listOf("a", "b", ""), listOf("c", "d", "")), grid(data.addColumn(5)))
        assertEquals(3, data.addColumn(-1).columnCount)
        assertEquals(listOf(listOf("b"), listOf("d")), grid(data.removeColumn(0)))
        assertEquals(listOf(listOf("a"), listOf("c")), grid(data.removeColumn(1)))
        assertSame(data, data.removeColumn(2))
        assertSame(data, data.removeColumn(-1))
        val single = table(listOf("a"), listOf("b"))
        assertSame(single, single.removeColumn(0))
    }

    @Test
    fun theHeaderRowIsSwitched() {
        val data = table(listOf("a"))
        assertFalse(data.withHeader(false).header)
        assertTrue(data.withHeader(false).withHeader(true).header)
    }

    @Test
    fun aDuplicateHasTheSameTextAndNewIds() {
        val data = table(listOf("a", "b"))
        val copy = data.duplicate()

        assertEquals(grid(data), grid(copy))
        assertNotEquals(data.rows[0].cells[0].id, copy.rows[0].cells[0].id)
        assertEquals(data.header, copy.header)
    }

    @Test
    fun tableBlocksHaveContentOnlyWithTextInSomeCell() {
        assertFalse(Block.TableBlock().hasContent())
        assertFalse(Block.TableBlock(MutableStateFlow(table(listOf("  ", ""), listOf(" ", "\t")))).hasContent())
        assertTrue(Block.TableBlock(MutableStateFlow(table(listOf("", ""), listOf("", "x")))).hasContent())
    }

    @Test
    fun codeBlocksHaveContentOnlyWithSomethingBesideBlanks() {
        assertFalse(Block.CodeBlock().hasContent())
        assertFalse(Block.CodeBlock(MutableStateFlow("   \n")).hasContent())
        assertTrue(Block.CodeBlock(MutableStateFlow("  x")).hasContent())
    }

    @Test
    fun theFactoryMakesEmptyCodeAndTableBlocks() {
        assertTrue(BlockFactory.createBlock(BlockType.CODE) is Block.CodeBlock)
        val made = BlockFactory.createBlock(BlockType.TABLE) as Block.TableBlock
        assertEquals(2 to 2, made.table.value.rowCount to made.table.value.columnCount)
    }

    @Test
    fun emptyForBackspaceMeansNoTextAnywhere() {
        assertTrue(BlockUtils.isBlockEmpty(Block.TableBlock()))
        assertFalse(BlockUtils.isBlockEmpty(Block.TableBlock(MutableStateFlow(table(listOf("", "x"))))))
        assertTrue(BlockUtils.isBlockEmpty(Block.CodeBlock()))
        assertFalse(BlockUtils.isBlockEmpty(Block.CodeBlock(MutableStateFlow("x"))))
    }
}
