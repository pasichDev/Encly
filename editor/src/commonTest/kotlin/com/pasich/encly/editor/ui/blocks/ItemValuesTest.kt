package com.pasich.encly.editor.ui.blocks

import com.pasich.encly.editor.model.ItemListBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ItemValuesTest {
    private val items = List(500) { ItemListBlock("item $it") }
    private val model = MutableStateFlow(items)
    private val values = ItemValues(model)

    @Test
    fun everyItemReadsItsTextFromOneMapBuiltOncePerList() {
        items.forEach { assertEquals(it.value, values.valueOf(it.id)) }

        assertEquals(1, values.builds)
    }

    @Test
    fun aNewListIsReadAgainAndAMissingItemIsNull() {
        values.valueOf(items[0].id)
        model.value = items.drop(1)

        assertEquals(null, values.valueOf(items[0].id))
        assertEquals(items[1].value, values.valueOf(items[1].id))
        assertEquals(2, values.builds)
    }

    @Test
    fun anItemFieldIsOnlyToldAboutItsOwnChanges() = runTest(UnconfinedTestDispatcher()) {
        val seen = mutableListOf<String?>()
        val job = launch { values.changes(items[7].id).collect { seen += it } }

        model.value = model.value.toMutableList().also { it[3] = it[3].copy(value = "other") }
        model.value = model.value.toMutableList().also { it[7] = it[7].copy(value = "mine") }
        job.cancel()

        assertEquals(listOf<String?>("item 7", "mine"), seen)
    }
}
