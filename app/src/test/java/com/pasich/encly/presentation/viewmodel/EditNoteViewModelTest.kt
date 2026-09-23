package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.domain.model.FontStyleType
import com.pasich.encly.domain.repository.SettingsRepository
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.presentation.editor.persistence.SaveStatusNote
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

    @Test
    fun titleEditIsAutosavedAfterTheDebounceOnTheInjectedDispatcher() = runTest {
        val repository = TestNotesRepository().apply { insertResult = INSERTED_ID }
        val viewModel = createViewModel(repository)
        advanceUntilIdle() // the first debounced emission only arms the autosave

        viewModel.updateTitle("draft")
        advanceUntilIdle()

        // The save ran to completion on the test scheduler: no real I/O thread is left
        // running to resume onto Dispatchers.Main after tearDown resets it.
        assertEquals(1, repository.insertCalls)
        assertEquals(INSERTED_ID, viewModel.state.value.note.id)
        assertEquals(SaveStatusNote.SAVED, viewModel.status.value)
    }

    private fun createViewModel(repository: TestNotesRepository, noteId: Long = -1L): EditNoteViewModel {
        val settingsRepository = mock(SettingsRepository::class.java)
        `when`(settingsRepository.fontSizeFlow).thenReturn(flowOf(DEFAULT_FONT_SIZE))
        `when`(settingsRepository.fontStyleFlow).thenReturn(flowOf(FontStyleType.MODERN_SIMPLE))
        `when`(settingsRepository.simpleEditFlow).thenReturn(flowOf(false))

        return EditNoteViewModel(
            notesRepository = repository,
            savedStateHandle = SavedStateHandle(mapOf("idNote" to noteId)),
            updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(repository),
            settingsRepository = settingsRepository,
            appScope = CoroutineScope(dispatcher),
            ioDispatcher = dispatcher,
            copyTitle = { "$it (Copy)" },
        )
    }

    private companion object {
        const val INSERTED_ID = 73L
        const val EXISTING_NOTE_ID = 91L
        const val DEFAULT_FONT_SIZE = 16
    }
}
