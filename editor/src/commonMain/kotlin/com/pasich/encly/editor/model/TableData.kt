package com.pasich.encly.editor.model

/**
 * One cell of a [TableData]. [id] keys the cell's field state in Compose, so adding or removing a row or
 * column can never hand one cell's text field to another. It survives `copy()` and is never serialized.
 */
public data class TableCell(val value: String = "", val id: String = newBlockId())

/** One row of a [TableData]: always as many [cells] as the table has columns. */
public data class TableRow(val cells: List<TableCell>)

/**
 * The content of a table block: [rows] of plain-text cells (at least 1 x 1, every row equally wide) and whether
 * the first row is a [header]. Immutable: every edit makes a new value, which is what the history stores. The
 * operations that would leave less than one row or column do nothing. A grid that is not rectangular, or has no
 * cell, is refused at construction ([of] pads one instead), so no cell lookup can meet a missing one.
 */
public data class TableData(val rows: List<TableRow>, val header: Boolean = true) {
    init {
        require(rows.isNotEmpty() && rows[0].cells.isNotEmpty()) { "A table has at least one cell" }
        require(rows.all { it.cells.size == rows[0].cells.size }) { "Every row of a table is as wide as the first" }
    }

    /** How many rows the table has. */
    val rowCount: Int get() = rows.size

    /** How many columns the table has. */
    val columnCount: Int get() = rows[0].cells.size

    /** The text of the cell at [row], [column]; empty outside the table. */
    public fun valueAt(row: Int, column: Int): String =
        rows.getOrNull(row)?.let { it.cells.getOrNull(column)?.value }.orEmpty()

    /** Where the cell [cellId] is, as row and column; null when it is not in the table. */
    public fun locate(cellId: String): Pair<Int, Int>? {
        rows.forEachIndexed { r, row ->
            val c = row.cells.indexOfFirst { it.id == cellId }
            if (c >= 0) return r to c
        }
        return null
    }

    /** The cell [cellId]; null when it is not in the table. */
    public fun cell(cellId: String): TableCell? = rows.firstNotNullOfOrNull { row ->
        row.cells.firstOrNull {
            it.id ==
                cellId
        }
    }

    /** This table with the text of cell [cellId] set to [value]; the same table when there is no such cell. */
    public fun withCell(cellId: String, value: String): TableData =
        copy(rows = rows.map { row -> TableRow(row.cells.map { if (it.id == cellId) it.copy(value = value) else it }) })

    /** This table with a new empty row at [at] (0 to [rowCount]; clamped). */
    public fun addRow(at: Int): TableData {
        val fresh = TableRow(List(columnCount) { TableCell() })
        val index = at.coerceIn(0, rowCount)
        return copy(rows = rows.take(index) + fresh + rows.drop(index))
    }

    /** This table without row [at]; unchanged when it is the only row or there is no such row. */
    public fun removeRow(at: Int): TableData =
        if (rowCount <= 1 || at !in rows.indices) this else copy(rows = rows.filterIndexed { i, _ -> i != at })

    /** This table with a new empty column at [at] (0 to [columnCount]; clamped). */
    public fun addColumn(at: Int): TableData {
        val index = at.coerceIn(0, columnCount)
        return copy(
            rows = rows.map { row -> TableRow(row.cells.take(index) + TableCell() + row.cells.drop(index)) },
        )
    }

    /** This table without column [at]; unchanged when it is the only column or there is no such column. */
    public fun removeColumn(at: Int): TableData = if (columnCount <= 1 || at !in 0 until columnCount) {
        this
    } else {
        copy(rows = rows.map { row -> TableRow(row.cells.filterIndexed { i, _ -> i != at }) })
    }

    /** This table with the header row switched on or off. */
    public fun withHeader(on: Boolean): TableData = copy(header = on)

    /** A copy that shares no cell identity with this table. */
    internal fun duplicate(): TableData =
        copy(rows = rows.map { row -> TableRow(row.cells.map { it.copy(id = newBlockId()) }) })

    /** Ways to make a table. */
    public companion object {
        /** A new table of [rows] x [columns] empty cells (each at least 1), with a header row. */
        public fun empty(rows: Int = 2, columns: Int = 2): TableData = TableData(
            List(rows.coerceAtLeast(1)) { TableRow(List(columns.coerceAtLeast(1)) { TableCell() }) },
        )

        /**
         * A table of [grid]'s rows, short rows padded with empty cells to the widest one; an empty grid gives a
         * 1 x 1 table.
         */
        public fun of(grid: List<List<String>>, header: Boolean = true): TableData {
            val width = grid.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
            val rows = grid.ifEmpty { listOf(emptyList()) }.map { row ->
                TableRow((row + List(width - row.size) { "" }).map { TableCell(it) })
            }
            return TableData(rows, header)
        }
    }
}
