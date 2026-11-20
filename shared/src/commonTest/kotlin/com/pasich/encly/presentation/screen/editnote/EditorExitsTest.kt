package com.pasich.encly.presentation.screen.editnote

import androidx.lifecycle.SavedStateHandle
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.note_delete_failed
import com.pasich.encly.core.resources.note_save_failed
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTags
import com.pasich.encly.domain.usecase.note.UpdateNoteArchiveStatusUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.editor.codec.BlockConverter
import com.pasich.encly.editor.model.Block
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.testutil.TestNotesRepository
import com.pasich.encly.testutil.TestSettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jetbrains.compose.resources.StringResource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The ways out of the editor write first and leave only once the write went through (issues #3 and #5): a slow
 * write holds the editor on screen, a failed one keeps it open and says so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorExitsTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = TestNotesRepository()
    private val place = RecordingPlace()
    private val failures = mutableListOf<StringResource>()
    private var beforeLeavingCalls = 0

    private class RecordingPlace : EditorPlace {
        var closed = 0
        override fun close() {
            closed += 1
        }

        override fun openCopy(copyId: Long) = Unit
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun backWaitsForASlowSaveBeforeLeaving() = runTest(dispatcher) {
        storeNote()
        val exits = exits(editorWithEdit())
        val gate = CompletableDeferred<Unit>()
        repository.updateGate = gate

        exits.back()
        runCurrent()

        // The write is under way and not finished: the editor stays, and says nothing yet.
        assertEquals(1, repository.updateCalls)
        assertEquals(0, place.closed)
        assertEquals(0, beforeLeavingCalls)
        assertTrue(failures.isEmpty())

        // A second Back (gesture and arrow) while it runs neither writes again nor pops twice.
        exits.back()
        runCurrent()
        assertEquals(1, repository.updateCalls)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, place.closed)
        assertEquals(1, beforeLeavingCalls)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun aFailedSaveKeepsTheEditorOpenWithTheErrorAndBackWorksAgainAfterwards() = runTest(dispatcher) {
        storeNote()
        val exits = exits(editorWithEdit())
        repository.updateResult = false

        exits.back()
        advanceUntilIdle()

        assertEquals(0, place.closed)
        assertEquals(0, beforeLeavingCalls)
        assertEquals(listOf(Res.string.note_save_failed), failures)

        repository.updateResult = true
        exits.back()
        advanceUntilIdle()

        assertEquals(1, place.closed)
        assertEquals(listOf(Res.string.note_save_failed), failures)
    }

    @Test
    fun aFailedFirstSaveOfANewNoteKeepsTheEditorOpenWithTheError() = runTest(dispatcher) {
        repository.insertResult = 0L
        val viewModel = viewModel(SavedStateHandle())
        advanceUntilIdle()
        viewModel.updateTitle("Never stored")
        val exits = exits(viewModel)

        exits.back()
        advanceUntilIdle()

        assertEquals(0, place.closed)
        assertEquals(listOf(Res.string.note_save_failed), failures)
    }

    @Test
    fun aFailedSingleNoteDeleteIsReportedNotShownAsSuccess() = runTest(dispatcher) {
        storeNote(isTrash = true)
        val viewModel = viewModel(SavedStateHandle(mapOf("idNote" to NOTE_ID, "isReadTrashOnly" to true)))
        advanceUntilIdle()
        val exits = exits(viewModel)
        repository.deleteNoteFails = true

        exits.delete()
        advanceUntilIdle()

        // The delete was tried, failed, and the editor neither left nor claimed it worked.
        assertEquals(listOf(NOTE_ID), repository.deletedIds)
        assertEquals(0, place.closed)
        assertEquals(0, beforeLeavingCalls)
        assertEquals(listOf(Res.string.note_delete_failed), failures)

        repository.deleteNoteFails = false
        exits.delete()
        advanceUntilIdle()

        assertEquals(1, place.closed)
        assertEquals(listOf(Res.string.note_delete_failed), failures)
    }

    private fun exits(viewModel: EditNoteViewModel) = EditorExits(
        viewModel = viewModel,
        where = place,
        scope = CoroutineScope(dispatcher),
        onFailure = { failures += it },
        beforeLeaving = { beforeLeavingCalls += 1 },
    )

    private fun TestScope.editorWithEdit(): EditNoteViewModel {
        val viewModel = viewModel(SavedStateHandle(mapOf("idNote" to NOTE_ID)))
        advanceUntilIdle()
        viewModel.updateTitle("Edited")
        return viewModel
    }

    private fun storeNote(isTrash: Boolean = false) {
        val note = Note(
            id = NOTE_ID,
            title = "Stored",
            value = BlockConverter.blocksToJson(listOf(Block.TextBlock(MutableStateFlow("body")))),
            isTrash = isTrash,
        )
        repository.allNotesWithTags.value = listOf(NoteWithTags(note, emptyList()))
    }

    private fun viewModel(handle: SavedStateHandle) = EditNoteViewModel(
        notesRepository = repository,
        savedStateHandle = handle,
        updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(repository),
        updateNoteArchiveStatusUseCase = UpdateNoteArchiveStatusUseCase(repository),
        settingsRepository = TestSettingsRepository(),
        appScope = CoroutineScope(dispatcher),
        ioDispatcher = dispatcher,
        copyTitle = { "$it (Copy)" },
    )

    private companion object {
        const val NOTE_ID = 12L
    }
}
