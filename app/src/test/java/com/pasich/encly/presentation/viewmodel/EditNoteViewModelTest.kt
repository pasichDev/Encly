package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.pasich.encly.data.datasource.local.FontStyleType
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.domain.usecase.note.UpdateNoteTagUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.domain.usecase.settings.FontSizeUseCase
import com.pasich.encly.domain.usecase.settings.FontStyleUseCase
import com.pasich.encly.domain.usecase.settings.SimpleEditUseCase
import com.pasich.encly.testutil.TestNotesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class EditNoteViewModelTest {
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
    fun firstSaveStoresInsertedIdAndSecondSaveUpdatesInsteadOfReinserting() = runTest {
        val repository = TestNotesRepository().apply { insertResult = INSERTED_ID }
        val viewModel = createViewModel(repository)

        viewModel.updateTitle("draft")

        assertTrue(viewModel.saveNote())
        assertEquals(INSERTED_ID, viewModel.state.value.note.id)
        assertEquals(1, repository.insertCalls)

        assertTrue(viewModel.saveNote())
        assertEquals(1, repository.insertCalls)
        assertEquals(1, repository.updateCalls)
    }

    @Test
    fun failedInsertIsNotReportedAsSaved() = runTest {
        val repository = TestNotesRepository().apply { insertResult = 0L }
        val viewModel = createViewModel(repository)

        viewModel.updateTitle("draft")

        assertFalse(viewModel.saveNote())
        assertEquals(-1L, viewModel.state.value.note.id)
        assertEquals(SaveStatusNote.OLD, viewModel.status.value)
    }

    @Test
    fun titleOnlyEmptyBlockPayloadIsValidAndDoesNotTriggerCorruptionGuard() = runTest {
        val note = Note(id = EXISTING_NOTE_ID, title = "title only", value = "[]")
        val repository = TestNotesRepository().apply {
            allNotesWithTags.value = listOf(NoteWithTag(note = note, tag = null))
        }
        val viewModel = createViewModel(repository, noteId = EXISTING_NOTE_ID)

        advanceUntilIdle()

        assertFalse(viewModel.contentLoadFailed.value)
        assertEquals(EXISTING_NOTE_ID, viewModel.state.value.note.id)
        assertEquals("title only", viewModel.state.value.note.title)
    }

    @Test
    fun malformedStoredBlockJsonTriggersCorruptionGuard() = runTest {
        val note = Note(id = EXISTING_NOTE_ID, title = "broken", value = "{not-json")
        val repository = TestNotesRepository().apply {
            allNotesWithTags.value = listOf(NoteWithTag(note = note, tag = null))
        }
        val viewModel = createViewModel(repository, noteId = EXISTING_NOTE_ID)

        advanceUntilIdle()

        assertTrue(viewModel.contentLoadFailed.value)
        assertEquals(SaveStatusNote.OLD, viewModel.status.value)
    }

    private fun createViewModel(
        repository: TestNotesRepository,
        noteId: Long = -1L
    ): EditNoteViewModel {
        val fontSizeUseCase = mock(FontSizeUseCase::class.java)
        val fontStyleUseCase = mock(FontStyleUseCase::class.java)
        val simpleEditUseCase = mock(SimpleEditUseCase::class.java)
        `when`(fontSizeUseCase.fontSizeFlow).thenReturn(flowOf(DEFAULT_FONT_SIZE))
        `when`(fontStyleUseCase.fontStyleFlow).thenReturn(flowOf(FontStyleType.MODERN_SIMPLE))
        `when`(simpleEditUseCase.simpleEditFlow).thenReturn(flowOf(false))

        return EditNoteViewModel(
            notesRepository = repository,
            savedStateHandle = SavedStateHandle(mapOf("idNote" to noteId)),
            updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(repository),
            updateNoteTagUseCase = UpdateNoteTagUseCase(repository),
            fontSizeUseCase = fontSizeUseCase,
            fontStyleUseCase = fontStyleUseCase,
            simpleEditUseCase = simpleEditUseCase,
            appScope = CoroutineScope(dispatcher)
        )
    }

    private companion object {
        const val INSERTED_ID = 73L
        const val EXISTING_NOTE_ID = 91L
        const val DEFAULT_FONT_SIZE = 16
    }
}
