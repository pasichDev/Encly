package com.pasich.encly.presentation.screen.trash

import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.usecase.note.TRASH_RETENTION_DAYS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrashCountdownTest {

    private fun trashed(id: Long, daysAgo: Int?, hours: Int = 0): Note = Note(
        id = id,
        title = "note $id",
        isTrash = true,
        trashedAt = daysAgo?.let { NOW - it * DAY - hours * HOUR },
    )

    @Test
    fun aNoteJustTrashedHasTheWholeRetention() {
        assertEquals(TRASH_RETENTION_DAYS, trashDaysLeft(NOW, NOW))
        assertEquals(TRASH_RETENTION_DAYS, trashDaysLeft(NOW - 23 * HOUR, NOW))
    }

    @Test
    fun eachFullDayInTheTrashTakesOneOff() {
        assertEquals(27, trashDaysLeft(NOW - DAY, NOW))
        assertEquals(3, trashDaysLeft(NOW - 25 * DAY - 5 * HOUR, NOW))
        assertEquals(1, trashDaysLeft(NOW - 27 * DAY - 23 * HOUR, NOW))
    }

    @Test
    fun aNoteDueOrOverdueHasNoneAndNeverLess() {
        assertEquals(0, trashDaysLeft(NOW - 28 * DAY, NOW))
        assertEquals(0, trashDaysLeft(NOW - 90 * DAY, NOW))
    }

    @Test
    fun aTrashTimeAheadOfTheClockIsTheWholeRetention() {
        assertEquals(TRASH_RETENTION_DAYS, trashDaysLeft(NOW + 5 * DAY, NOW))
    }

    @Test
    fun aNoteWithoutATrashTimeHasTheWholeRetention() {
        assertEquals(TRASH_RETENTION_DAYS, trashDaysLeft(null, NOW))
    }

    @Test
    fun notesAreListedSoonestFirstInTwoSections() {
        val countdown = trashCountdown(
            listOf(
                trashed(1, daysAgo = 0),
                trashed(2, daysAgo = 25),
                trashed(3, daysAgo = 14),
                trashed(4, daysAgo = 22),
            ),
            NOW,
        )

        assertEquals(listOf(2L to 3, 4L to 6), countdown.soon.map { it.note.id to it.daysLeft })
        assertEquals(listOf(3L to 14, 1L to 28), countdown.later.map { it.note.id to it.daysLeft })
        assertEquals(4, countdown.count)
        assertEquals(3, countdown.firstDays)
        assertEquals(28, countdown.lastDays)
    }

    @Test
    fun aWeekLeftIsStillSoonAndEightDaysIsLater() {
        val countdown = trashCountdown(listOf(trashed(1, daysAgo = 21), trashed(2, daysAgo = 20)), NOW)

        assertEquals(listOf(7), countdown.soon.map { it.daysLeft })
        assertEquals(listOf(8), countdown.later.map { it.daysLeft })
    }

    @Test
    fun anEntryKeepsItsPlaceInTheListItCameFrom() {
        val countdown = trashCountdown(listOf(trashed(1, daysAgo = 0), trashed(2, daysAgo = 25)), NOW)

        // Selection toggles by that place, whatever order the rows are shown in.
        assertEquals(1, countdown.soon.single().index)
        assertEquals(0, countdown.later.single().index)
    }

    @Test
    fun notesWithTheSameDaysGoByTrashTimeThenId() {
        val countdown = trashCountdown(
            listOf(
                trashed(5, daysAgo = 2, hours = 1),
                trashed(4, daysAgo = 2, hours = 3),
                trashed(3, daysAgo = 2, hours = 3),
            ),
            NOW,
        )

        assertEquals(listOf(3L, 4L, 5L), countdown.later.map { it.note.id })
    }

    @Test
    fun aNoteWithoutATrashTimeIsListedLastAmongTheLatest() {
        val countdown =
            trashCountdown(listOf(trashed(1, daysAgo = null), trashed(2, daysAgo = 0), trashed(3, daysAgo = 26)), NOW)

        assertEquals(listOf(3L), countdown.soon.map { it.note.id })
        assertEquals(listOf(2L, 1L), countdown.later.map { it.note.id })
        assertEquals(TRASH_RETENTION_DAYS, countdown.later.last().daysLeft)
    }

    @Test
    fun anEmptyTrashHasNoSectionsAndNoDays() {
        val countdown = trashCountdown(emptyList(), NOW)

        assertTrue(countdown.soon.isEmpty() && countdown.later.isEmpty())
        assertEquals(0, countdown.count)
        assertNull(countdown.firstDays)
        assertNull(countdown.lastDays)
    }

    @Test
    fun aTrashOfSoonNotesOnlyStillHasItsFirstAndLast() {
        val countdown = trashCountdown(listOf(trashed(1, daysAgo = 27), trashed(2, daysAgo = 23)), NOW)

        assertEquals(1, countdown.firstDays)
        assertEquals(5, countdown.lastDays)
    }

    private companion object {
        const val HOUR = 60L * 60 * 1000
        const val DAY = 24 * HOUR
        const val NOW = 1_800_000_000_000L
    }
}
