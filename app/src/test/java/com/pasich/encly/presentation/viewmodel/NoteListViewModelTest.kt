package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.repository.SettingsRepository
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.repository.TagSelectionRepository
import com.pasich.encly.domain.usecase.note.GetAllNotesUseCase
import com.pasich.encly.domain.usecase.note.GetNotesByTagUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteDescriptionUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTagUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.domain.usecase.settings.GetNoteSortOptionUseCase
import com.pasich.encly.domain.usecase.settings.ToggleNoteSortTagUseCase
import com.pasich.encly.testutil.TestNotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class NoteListViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun staleAllNotesFlowCannotOverwriteSelectedTagResults() = runTest {
        val repository = TestNotesRepository()
        val settings = mock(SettingsRepository::class.java)
        val sortFlow = MutableStateFlow(NoteSortOption.UPDATED_DESC)
        `when`(settings.getSortNotes).thenReturn(sortFlow)

        val tagSelection = TagSelectionRepository()
        val viewModel = NoteListViewModel(
            getAllNotesUseCase = GetAllNotesUseCase(repository),
            getNotesByTagUseCase = GetNotesByTagUseCase(repository),
            updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(repository),
            updateNoteTagUseCase = UpdateNoteTagUseCase(repository),
            tagSelectionRepository = tagSelection,
            toggleNoteSortTagUseCase = ToggleNoteSortTagUseCase(settings),
            getNoteSortOptionUseCase = GetNoteSortOptionUseCase(settings),
            updateNoteDescriptionUseCase = UpdateNoteDescriptionUseCase(repository)
        )

        repository.allNotesWithTags.value = listOf(noteWithTitle(ALL_NOTE_ID, "all"))
        advanceUntilIdle()
        assertEquals("all", viewModel.state.value.notes.single().note.title)

        val workTag = Tag(id = WORK_TAG_ID, nameTag = "Work")
        repository.tagFlow(WORK_TAG_ID).value =
            listOf(noteWithTitle(TAGGED_NOTE_ID, "tagged", workTag))
        viewModel.onEvent(NoteListEvent.SelectTag(workTag))
        advanceUntilIdle()

        assertEquals(WORK_TAG_ID, viewModel.state.value.selectedTag)
        assertEquals("tagged", viewModel.state.value.notes.single().note.title)

        repository.allNotesWithTags.value = listOf(noteWithTitle(STALE_NOTE_ID, "stale"))
        advanceUntilIdle()

        assertEquals(WORK_TAG_ID, viewModel.state.value.selectedTag)
        assertEquals("tagged", viewModel.state.value.notes.single().note.title)
    }

    private fun noteWithTitle(id: Long, title: String, tag: Tag? = null): NoteWithTag =
        NoteWithTag(
            note = Note(
                id = id,
                title = title,
                date = id,
                dateCreate = id,
                tagId = tag?.id
            ),
            tag = tag
        )

    private companion object {
        const val WORK_TAG_ID = 7L
        const val ALL_NOTE_ID = 11L
        const val TAGGED_NOTE_ID = 12L
        const val STALE_NOTE_ID = 13L
    }
}
