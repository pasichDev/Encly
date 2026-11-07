package com.pasich.encly.editor.codec

import com.pasich.encly.editor.model.TableData
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/** A table block in the stored JSON: `hasHeader`, and `rows`, an array of arrays of cell texts. */
internal object TableJson {
    /** The `rows` of [table]. */
    fun rows(table: TableData): JsonArray =
        JsonArray(table.rows.map { row -> JsonArray(row.cells.map { JsonPrimitive(it.value) }) })

    /** The table stored as [rows] (absent: one empty cell) and [hasHeader] (absent: with a header). */
    fun decode(rows: JsonArray?, hasHeader: String?): TableData {
        val grid = rows?.map { row ->
            (row as? JsonArray ?: throw SerializationException("Expected an array of cells")).map(::cellText)
        }.orEmpty()
        return TableData.of(grid, hasHeader?.toBoolean() ?: true)
    }

    private fun cellText(cell: JsonElement): String {
        if (cell !is JsonPrimitive || cell is JsonNull) throw SerializationException("Expected a cell text")
        return cell.content
    }
}
