package com.pasich.encly.domain.usecase.note

import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.model.NoteListItem
import com.pasich.encly.domain.repository.NotesRepository
import com.pasich.encly.testutil.TestNotesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveNotesUseCaseTest {

    @Test
    fun aDatabaseReadFailureFailsTheFlowInsteadOfEmittingNoNotes() = runTest {
        val failing = object : NotesRepository by TestNotesRepository() {
            override fun getAllNotesWithTag(): Flow<List<NoteWithTag>> = flow { throw IOException("vault closed") }
        }

        val emitted = mutableListOf<List<NoteListItem>>()
        val thrown = runCatching {
            ObserveNotesUseCase(failing, StandardTestDispatcher(testScheduler))(
                tagId = null,
                sortOption = NoteSortOption.UPDATED_DESC,
            ).toList(emitted)
        }.exceptionOrNull()

        // The screen turns the failure into an error state (asLoadState); never an empty list.
        assertTrue(thrown is IOException)
        assertTrue(emitted.isEmpty())
    }

    @Test
    fun allNotesLeaveOutHiddenTagsAndFollowTheSortOption() = runTest {
        val hidden = Tag(id = 1, nameTag = "Private", isVisible = false)
        val repository = TestNotesRepository().apply {
            allNotesWithTags.value = listOf(
                noteWithTag(id = 1, date = 10),
                noteWithTag(id = 2, date = 30),
                noteWithTag(id = 3, date = 20, tag = hidden),
            )
        }

        val newestFirst = loaded(repository, NoteSortOption.UPDATED_DESC)
        val oldestFirst = loaded(repository, NoteSortOption.UPDATED_ASC)

        assertEquals(listOf(2L, 1L), newestFirst.map { it.note.id })
        assertEquals(listOf(1L, 2L), oldestFirst.map { it.note.id })
    }

    @Test
    fun oneUnreadableNoteDoesNotTakeTheListDown() = runTest {
        val repository = TestNotesRepository().apply {
            allNotesWithTags.value = listOf(
                noteWithTag(id = 1, date = 2, value = """[{"blockType":"TEXT","text":"fine"}]"""),
                noteWithTag(id = 2, date = 1, value = """[{"blockType":"FUTURE"}]"""),
            )
        }

        val items = loaded(repository, NoteSortOption.UPDATED_DESC)

        assertEquals("fine", items[0].preview)
        assertNull(items[1].preview)
    }

    private suspend fun TestScope.loaded(repository: NotesRepository, sortOption: NoteSortOption): List<NoteListItem> =
        ObserveNotesUseCase(repository, StandardTestDispatcher(testScheduler))(null, sortOption).first()

    private fun noteWithTag(id: Long, date: Long, tag: Tag? = null, value: String = "") = NoteWithTag(
        note = Note(id = id, title = "n$id", value = value, date = date, dateCreate = date, tagId = tag?.id),
        tag = tag,
    )
}
