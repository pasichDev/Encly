@file:Suppress("TooManyFunctions") // One small function per table command.

package com.pasich.encly.editor.state

import com.pasich.encly.editor.model.TableData

// What editing a table is, apart from drawing it: where a key takes the cursor, and what each command of the cell
// menu does to the grid. Pure functions of a [TableData]; the block hands the result to the editor as one edit.

/**
 * The [TypingStep] of a table edit from [old] to [new]: typing in one cell, else null (a row or a column added or
 * removed, the header switched, is a step of its own).
 */
public fun tableTypingStep(old: TableData, new: TableData): TypingStep? {
    val sameShape = old.rowCount == new.rowCount && old.columnCount == new.columnCount && old.header == new.header
    val changed = if (sameShape) old.cells().zip(new.cells()).filter { (a, b) -> a != b } else emptyList()
    val cell = changed.singleOrNull()?.takeIf { (a, b) -> a.id == b.id } ?: return null
    return typingStep(cell.first.id, cell.first.value, cell.second.value)
}

private fun TableData.cells() = rows.flatMap { it.cells }

/** A step of the cursor between the cells of a table, in reading order or along a column. */
internal enum class CellStep { PREVIOUS, NEXT, UP, DOWN }

/** The id of the cell [step] away from [cellId]; null at the edge of the table or for a cell that is not in it. */
internal fun TableData.neighborOf(cellId: String, step: CellStep): String? {
    val (row, column) = locate(cellId) ?: return null
    val target = when (step) {
        CellStep.PREVIOUS -> if (column > 0) row to column - 1 else (row - 1) to columnCount - 1
        CellStep.NEXT -> if (column < columnCount - 1) row to column + 1 else (row + 1) to 0
        CellStep.UP -> (row - 1) to column
        CellStep.DOWN -> (row + 1) to column
    }
    return rows.getOrNull(target.first)?.let { it.cells[target.second].id }
}

/** The grid [table] an edit produced, and the cell the cursor goes to ([focusCellId]; null leaves it). */
internal class TableEdit(val table: TableData, val focusCellId: String?)

/** Enter in [cellId]: the cell below, a new row first when this is the last one. */
internal fun TableData.enterFrom(cellId: String): TableEdit? {
    val (row, column) = locate(cellId) ?: return null
    return if (row < rowCount - 1) {
        TableEdit(this, rows[row + 1].cells[column].id)
    } else {
        addRowBelow(cellId)
    }
}

/** Tab in [cellId]: the next cell; after the last one a new row, the cursor in its first cell. */
internal fun TableData.tabFrom(cellId: String): TableEdit? {
    val next = neighborOf(cellId, CellStep.NEXT)
    if (next != null || locate(cellId) == null) return next?.let { TableEdit(this, it) }
    val grown = addRow(rowCount)
    return TableEdit(grown, grown.rows.last().cells.first().id)
}

/** A new row under the row of [cellId], the cursor in its cell of the same column. */
internal fun TableData.addRowBelow(cellId: String): TableEdit? {
    val (row, column) = locate(cellId) ?: return null
    val grown = addRow(row + 1)
    return TableEdit(grown, grown.rows[row + 1].cells[column].id)
}

/** A new column after the column of [cellId], in reading order (so on its left in a right-to-left table). */
internal fun TableData.addColumnAfter(cellId: String): TableEdit? {
    val (row, column) = locate(cellId) ?: return null
    val grown = addColumn(column + 1)
    return TableEdit(grown, grown.rows[row].cells[column + 1].id)
}

/** The row of [cellId] removed, the cursor in the same column of the row now there (above, for the last row). */
internal fun TableData.removeRowOf(cellId: String): TableEdit? = locate(cellId)?.let { (row, column) ->
    removeRow(row).takeIf { it != this }?.let { shrunk ->
        TableEdit(shrunk, shrunk.rows[row.coerceAtMost(shrunk.rowCount - 1)].cells[column].id)
    }
}

/** The column of [cellId] removed, the cursor in the same row of the column now there (before, for the last one). */
internal fun TableData.removeColumnOf(cellId: String): TableEdit? = locate(cellId)?.let { (row, column) ->
    removeColumn(column).takeIf { it != this }?.let { shrunk ->
        TableEdit(shrunk, shrunk.rows[row].cells[column.coerceAtMost(shrunk.columnCount - 1)].id)
    }
}

/** The header row switched, the cursor left where it is. */
internal fun TableData.toggleHeader(): TableEdit = TableEdit(withHeader(!header), null)

/** Where the cursor goes after an undo or redo that changed a cell's text: that cell, after the changed part. */
internal fun tableCaret(blockId: String, before: TableData, after: TableData): CaretTarget? =
    after.cells().firstNotNullOfOrNull { cell ->
        before.cell(cell.id)?.let { old -> changedCaret(old.value, cell.value) }
            ?.let { CaretTarget(blockId, cell.id, it) }
    }

/** A line of text as a cell holds it: no line breaks (they become one space each). */
internal fun cellText(text: String): String = normalizeLineBreaks(text).replace(LINE_BREAKS, " ")

private val LINE_BREAKS = Regex("\n+")
