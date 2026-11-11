package com.pasich.encly.presentation.screen.trash

import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.usecase.note.TRASH_RETENTION_DAYS

/** A note with this many days left, or fewer, is listed under "Gone within a week". */
internal const val TRASH_SOON_DAYS = 7

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

/** A trashed note, where it stands in the screen's state ([index]) and the days it has left. */
internal data class TrashEntry(val note: Note, val index: Int, val daysLeft: Int)

/** The trash as a countdown: the notes that go within a week, then the rest, each soonest first. */
internal data class TrashCountdown(val soon: List<TrashEntry>, val later: List<TrashEntry>) {
    val count: Int get() = soon.size + later.size

    /** The days left of the note that goes first, null in an empty trash. */
    val firstDays: Int? get() = (soon.firstOrNull() ?: later.firstOrNull())?.daysLeft

    /** The days left of the note that goes last, null in an empty trash. */
    val lastDays: Int? get() = (later.lastOrNull() ?: soon.lastOrNull())?.daysLeft
}

/**
 * The whole days a note trashed at [trashedAt] has before it is deleted for good: the retention less the
 * full days it has spent in the trash, never below 0 (its last day, or overdue until the next purge) and
 * never above the retention (a trash time ahead of the clock). A note without a trash time (imported, or
 * restored from a backup made before the time was kept) has the full retention: its days start when the
 * next purge stamps it.
 */
internal fun trashDaysLeft(trashedAt: Long?, now: Long): Int {
    if (trashedAt == null) return TRASH_RETENTION_DAYS
    val spent = (now - trashedAt).coerceAtLeast(0L) / MILLIS_PER_DAY
    return (TRASH_RETENTION_DAYS - spent).coerceIn(0L, TRASH_RETENTION_DAYS.toLong()).toInt()
}

/**
 * [notes] in the order they will go: by days left, then by the time they were trashed (a note without one
 * after those that have it), then by id so the order never depends on how the list arrived.
 */
internal fun trashCountdown(notes: List<Note>, now: Long): TrashCountdown {
    val entries = notes
        .mapIndexed { index, note -> TrashEntry(note, index, trashDaysLeft(note.trashedAt, now)) }
        .sortedWith(
            compareBy<TrashEntry> { it.daysLeft }
                .thenBy { it.note.trashedAt ?: Long.MAX_VALUE }
                .thenBy { it.note.id },
        )
    val (soon, later) = entries.partition { it.daysLeft <= TRASH_SOON_DAYS }
    return TrashCountdown(soon, later)
}
