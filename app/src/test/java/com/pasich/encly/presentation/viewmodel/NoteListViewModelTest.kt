package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.core.common.LoadState
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.repository.NotesRepository
import com.pasich.encly.domain.repository.SettingsRepository
import com.pasich.encly.domain.usecase.note.ObserveNotesUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteDescriptionUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTagUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.testutil.TestNotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.IOException

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

        val tagSelection = SelectedTagHolder()
        val viewModel = NoteListViewModel(
            observeNotesUseCase = ObserveNotesUseCase(repository, dispatcher),
            updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(repository),
            updateNoteTagUseCase = UpdateNoteTagUseCase(repository),
            selectedTagHolder = tagSelection,
            settingsRepository = settings,
            updateNoteDescriptionUseCase = UpdateNoteDescriptionUseCase(repository),
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

    @Test
    fun anEmptyVaultIsReadyAndEmptyNotFailed() = runTest {
        val repository = TestNotesRepository()
        val settings = mock(SettingsRepository::class.java)
        `when`(settings.getSortNotes).thenReturn(MutableStateFlow(NoteSortOption.UPDATED_DESC))
        val viewModel = NoteListViewModel(
            observeNotesUseCase = ObserveNotesUseCase(repository, dispatcher),
            updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(repository),
            updateNoteTagUseCase = UpdateNoteTagUseCase(repository),
            selectedTagHolder = SelectedTagHolder(),
            settingsRepository = settings,
            updateNoteDescriptionUseCase = UpdateNoteDescriptionUseCase(repository),
        )
        assertEquals(LoadState.Loading, viewModel.state.value.notesLoad)

        advanceUntilIdle()

        assertEquals(LoadState.Ready(emptyList<Any>()), viewModel.state.value.notesLoad)
    }

    @Test
    fun aFailedReadIsShownAsAnErrorNotAsAnEmptyVault() = runTest {
        val failing = object : NotesRepository by TestNotesRepository() {
            override fun getAllNotesWithTag(): Flow<List<NoteWithTag>> = flow { throw IOException("vault closed") }
        }
        val settings = mock(SettingsRepository::class.java)
        `when`(settings.getSortNotes).thenReturn(MutableStateFlow(NoteSortOption.UPDATED_DESC))
        val viewModel = NoteListViewModel(
            observeNotesUseCase = ObserveNotesUseCase(failing, dispatcher),
            updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(failing),
            updateNoteTagUseCase = UpdateNoteTagUseCase(failing),
            selectedTagHolder = SelectedTagHolder(),
            settingsRepository = settings,
            updateNoteDescriptionUseCase = UpdateNoteDescriptionUseCase(failing),
        )

        advanceUntilIdle()

        val load = viewModel.state.value.notesLoad
        assertTrue(load is LoadState.Failed)
        assertEquals(ListLoadErrors.NOTES, (load as LoadState.Failed).error)
        assertTrue(viewModel.state.value.notes.isEmpty())
    }

    private fun noteWithTitle(id: Long, title: String, tag: Tag? = null): NoteWithTag = NoteWithTag(
        note = Note(
            id = id,
            title = title,
            date = id,
            dateCreate = id,
            tagId = tag?.id,
        ),
        tag = tag,
    )

    private companion object {
        const val WORK_TAG_ID = 7L
        const val ALL_NOTE_ID = 11L
        const val TAGGED_NOTE_ID = 12L
        const val STALE_NOTE_ID = 13L
    }
}
