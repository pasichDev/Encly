package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.core.common.LoadState
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.domain.model.NoteListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteListEmptyStateTest {

    private val note = NoteListItem(NoteWithTag(Note(id = 1, title = "a"), null), preview = "", searchText = "")

    @Test
    fun anEmptyVaultSaysNoNotes() {
        val state = NoteListState(notesLoad = LoadState.Ready(emptyList()))
        assertEquals(NotesEmptyState.NoNotes, state.emptyState)
    }

    @Test
    fun anEmptyTagNamesTheTag() {
        val state = NoteListState(selectedTag = 7, selectedTagName = "Work", notesLoad = LoadState.Ready(emptyList()))
        assertEquals(NotesEmptyState.NoneTagged("Work"), state.emptyState)
    }

    @Test
    fun noEmptyStateWhileLoadingOrWithNotes() {
        assertNull(NoteListState(selectedTag = 7, notesLoad = LoadState.Loading).emptyState)
        assertNull(NoteListState(notesLoad = LoadState.Ready(listOf(note))).emptyState)
    }
}
