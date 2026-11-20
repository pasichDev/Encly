package com.pasich.encly.editor.ui.blocks

import com.pasich.encly.editor.model.TableData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CellValuesTest {
    private val start = TableData.of(List(40) { r -> List(5) { c -> "r$r c$c" } })
    private val model = MutableStateFlow(start)
    private val values = CellValues(model)
    private val cells = start.rows.flatMap { it.cells }

    @Test
    fun everyCellReadsItsTextFromOneMapBuiltOncePerTable() {
        cells.forEach { assertEquals(it.value, values.valueOf(it.id)) }

        assertEquals(1, values.builds)
    }

    @Test
    fun aNewTableIsReadAgainAndAMissingCellIsNull() {
        values.valueOf(cells[0].id)
        model.value = start.removeRow(0)

        assertEquals(null, values.valueOf(cells[0].id))
        assertEquals(cells[5].value, values.valueOf(cells[5].id))
        assertEquals(2, values.builds)
    }

    @Test
    fun aCellFieldIsOnlyToldAboutItsOwnChanges() = runTest(UnconfinedTestDispatcher()) {
        val seen = mutableListOf<String?>()
        val job = launch { values.changes(cells[7].id).collect { seen += it } }

        model.value = model.value.withCell(cells[3].id, "other")
        model.value = model.value.withCell(cells[7].id, "mine")
        job.cancel()

        assertEquals(listOf<String?>("r1 c2", "mine"), seen)
    }
}
