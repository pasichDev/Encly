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

    @Test
    fun storedIndexesKeepTheirMeaningAndTheNewOrdersAreAppended() {
        assertEquals(
            listOf(
                NoteSortOption.UPDATED_ASC,
                NoteSortOption.UPDATED_DESC,
                NoteSortOption.CREATED_ASC,
                NoteSortOption.CREATED_DESC,
                NoteSortOption.TITLE_ASC,
                NoteSortOption.TITLE_DESC,
            ),
            (0..5).map(NoteSortOption::fromIndex),
        )
        assertEquals(NoteSortOption.UPDATED_DESC, NoteSortOption.fromIndex(99))
    }

    private fun titled(id: Long, title: String, date: Long = id) = Note(id = id, title = title, date = date)

    @Test
    fun titlesSortIgnoringCaseInBothDirections() {
        val notes = listOf(titled(1, "banana"), titled(2, "Apple"), titled(3, "cherry"), titled(4, "Banana "))

        assertEquals(listOf(2L, 4L, 1L, 3L), notes.sortedWith(NoteSortOption.TITLE_ASC.comparator()).map { it.id })
        assertEquals(listOf(3L, 4L, 1L, 2L), notes.sortedWith(NoteSortOption.TITLE_DESC.comparator()).map { it.id })
    }

    @Test
    fun equalTitlesFallBackToTheNewestEditFirst() {
        val notes = listOf(titled(1, "Same", date = 10), titled(2, "same", date = 30), titled(3, "SAME", date = 20))

        assertEquals(listOf(2L, 3L, 1L), notes.sortedWith(NoteSortOption.TITLE_ASC.comparator()).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), notes.sortedWith(NoteSortOption.TITLE_DESC.comparator()).map { it.id })
    }

    @Test
    fun untitledNotesComeAfterTheTitledOnesWhicheverWayAndKeepTheirEditOrder() {
        val notes = listOf(titled(1, "", date = 10), titled(2, "Zed"), titled(3, "   ", date = 50), titled(4, "Alpha"))

        assertEquals(listOf(4L, 2L, 3L, 1L), notes.sortedWith(NoteSortOption.TITLE_ASC.comparator()).map { it.id })
        assertEquals(listOf(2L, 4L, 3L, 1L), notes.sortedWith(NoteSortOption.TITLE_DESC.comparator()).map { it.id })
    }
}
