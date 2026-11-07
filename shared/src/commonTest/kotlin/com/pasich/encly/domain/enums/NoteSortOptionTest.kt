package com.pasich.encly.domain.enums

import com.pasich.encly.data.model.Note
import kotlin.test.Test
import kotlin.test.assertEquals

class NoteSortOptionTest {
    private val first = Note(id = 1, date = 500L, dateCreate = 100L)
    private val second = Note(id = 2, date = 900L, dateCreate = 300L)

    // Lost its creation time: only the last edit is known.
    private val lost = Note(id = 3, date = 200L, dateCreate = 0L)

    @Test
    fun creationOrderFallsBackToTheEarliestKnownTimeNotToZero() {
        val notes = listOf(second, lost, first)

        assertEquals(listOf(1L, 3L, 2L), notes.sortedWith(NoteSortOption.CREATED_ASC.comparator()).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), notes.sortedWith(NoteSortOption.CREATED_DESC.comparator()).map { it.id })
    }

    @Test
    fun editOrderIgnoresTheCreationTime() {
        val notes = listOf(first, second, lost)

        assertEquals(listOf(3L, 1L, 2L), notes.sortedWith(NoteSortOption.UPDATED_ASC.comparator()).map { it.id })
        assertEquals(listOf(2L, 1L, 3L), notes.sortedWith(NoteSortOption.UPDATED_DESC.comparator()).map { it.id })
    }
}
