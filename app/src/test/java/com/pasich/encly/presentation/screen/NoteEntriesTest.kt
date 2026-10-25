package com.pasich.encly.presentation.screen

import com.pasich.encly.R
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.domain.model.NoteListItem
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteEntriesTest {

    private fun item(id: Long, pinned: Boolean = false) =
        NoteListItem(NoteWithTag(Note(id = id, isPinned = pinned), null), preview = null, searchText = "")

    private fun describe(entries: List<NoteEntry>) = entries.map {
        when (it) {
            is NoteSection -> if (it.title == R.string.notes_section_pinned) "Pinned" else "Others"
            is NoteRow -> it.item.note.id.toString()
        }
    }

    @Test
    fun withNoPinnedNoteThereAreNoHeadings() {
        val entries = noteEntries(listOf(item(1), item(2)), sections = true)

        assertEquals(listOf("1", "2"), describe(entries))
    }

    @Test
    fun pinnedNotesSitUnderPinnedAndTheRestUnderOthers() {
        val entries = noteEntries(listOf(item(1, pinned = true), item(2, pinned = true), item(3), item(4)), true)

        assertEquals(listOf("Pinned", "1", "2", "Others", "3", "4"), describe(entries))
    }

    @Test
    fun onlyPinnedNotesNeedNoOthersHeading() {
        val entries = noteEntries(listOf(item(1, pinned = true)), sections = true)

        assertEquals(listOf("Pinned", "1"), describe(entries))
    }

    @Test
    fun searchResultsKeepTheirOrderWithoutHeadings() {
        val entries = noteEntries(listOf(item(1, pinned = true), item(2)), sections = false)

        assertEquals(listOf("1", "2"), describe(entries))
    }

    @Test
    fun rowsAreKeyedByNoteIdAndHeadingsByTheirTitle() {
        val entries = noteEntries(listOf(item(7, pinned = true), item(8)), sections = true)

        assertEquals(
            listOf<Any>("section-${R.string.notes_section_pinned}", 7L, "section-${R.string.notes_section_other}", 8L),
            entries.map {
                it.key
            },
        )
    }
}
