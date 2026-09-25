package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.presentation.editor.persistence.SaveStatusNote
import com.pasich.encly.testutil.TestNotesRepository
import com.pasich.encly.testutil.TestSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

/** The editor around its content: arguments, settings, saves on leaving, lock and status. */
@OptIn(ExperimentalCoroutinesApi::class)
class EditNoteViewModelLifecycleTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = TestNotesRepository().apply { insertResult = INSERTED_ID }
    private val settings = TestSettingsRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aNoteCreatedFromATagsListStartsInThatTag() = runTest(dispatcher) {
        val viewModel = viewModel(SavedStateHandle(mapOf("addTag" to TAG_ID)))
        advanceUntilIdle()

        assertEquals(TAG_ID, viewModel.state.value.note.tagId)
        assertTrue(viewModel.isNewNote)
    }

    @Test
    fun clearingTheScreenSavesWhatWasTypedOnce() = runTest(dispatcher) {
        val store = ViewModelStore()
        val viewModel = viewModel(SavedStateHandle())
        store.put("editor", viewModel)
        advanceUntilIdle()
        viewModel.updateTitle("Typed just before leaving")

        store.clear()
        advanceUntilIdle()

        assertEquals("Typed just before leaving", repository.insertedNotes.single().title)
    }

    @Test
    fun aHandledExitStopsTheLastChanceSave() = runTest(dispatcher) {
        val store = ViewModelStore()
        val viewModel = viewModel(SavedStateHandle())
        store.put("editor", viewModel)
        advanceUntilIdle()
        viewModel.updateTitle("Discarded")

        viewModel.markExitHandled()
        store.clear()
        advanceUntilIdle()

        assertEquals(0, repository.insertCalls)
    }

    @Test
    fun goingToTheBackgroundSavesWithoutWaitingForThePause() = runTest(dispatcher) {
        val viewModel = viewModel(SavedStateHandle())
        advanceUntilIdle()
        viewModel.updateTitle("Before the re-lock")

        viewModel.saveForBackground()
        dispatcher.scheduler.runCurrent()
        advanceUntilIdle()

        assertEquals(1, repository.insertCalls)
        assertEquals(SaveStatusNote.SAVED, viewModel.status.value)
    }

    @Test
    fun aFailedSaveIsShownAsNotSaved() = runTest(dispatcher) {
        repository.insertResult = 0
        val viewModel = viewModel(SavedStateHandle())
        advanceUntilIdle()
        viewModel.updateTitle("Will fail")

        assertFalse(viewModel.saveNote())

        assertEquals(SaveStatusNote.FAILED, viewModel.status.value)
    }

    @Test
    fun theFontSizeIsStoredAndFollowed() = runTest(dispatcher) {
        val viewModel = viewModel(SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.fontSize.collect {} }
        advanceUntilIdle()

        viewModel.updateFontSize(FONT_SIZE)
        advanceUntilIdle()

        assertEquals(FONT_SIZE, settings.fontSizeFlow.value)
        assertEquals(FONT_SIZE, viewModel.fontSize.value)
    }

    @Test
    fun theLockToggleLocksAndUnlocksAnOpenedNote() = runTest(dispatcher) {
        storeNote()
        val viewModel = viewModel(SavedStateHandle(mapOf("idNote" to NOTE_ID)))
        advanceUntilIdle()
        assertFalse(viewModel.lockEditor.value)

        viewModel.toggleLockEditor()
        assertTrue(viewModel.lockEditor.value)
        viewModel.toggleLockEditor()
        assertFalse(viewModel.lockEditor.value)
    }

    @Test
    fun aNoteInTheTrashCannotBeUnlocked() = runTest(dispatcher) {
        storeNote(isTrash = true)
        val viewModel = viewModel(
            SavedStateHandle(mapOf("idNote" to NOTE_ID, "isReadTrashOnly" to true)),
        )
        advanceUntilIdle()

        viewModel.toggleLockEditor()

        assertTrue(viewModel.lockEditor.value)
    }

    @Test
    fun hasChangesIsWhatDiscardWouldUndo() = runTest(dispatcher) {
        storeNote()
        val viewModel = viewModel(SavedStateHandle(mapOf("idNote" to NOTE_ID)))
        advanceUntilIdle()
        assertFalse(viewModel.hasChanges())

        viewModel.onBlockTextChanged(viewModel.blocks.first(), "changed")
        assertTrue(viewModel.hasChanges())

        assertTrue(viewModel.discardChanges())
        advanceUntilIdle()
        assertEquals(
            "body",
            BlockConverter.jsonToBlocksOrNull(repository.updatedNotes.last().value)!!.let {
                (it.single() as Block.TextBlock).text.value
            },
        )
    }

    @Test
    fun deletingAStoredNoteRemovesIt() = runTest(dispatcher) {
        storeNote(isTrash = true)
        val viewModel = viewModel(SavedStateHandle(mapOf("idNote" to NOTE_ID, "isReadTrashOnly" to true)))
        advanceUntilIdle()

        assertTrue(viewModel.noteDelete())

        assertEquals(listOf(NOTE_ID), repository.deletedIds)
    }

    @Test
    fun undoAndRedoFollowTheEdits() = runTest(dispatcher) {
        val viewModel = viewModel(SavedStateHandle())
        advanceUntilIdle()
        assertFalse(viewModel.canUndo.value)

        viewModel.onBlockTextChanged(viewModel.blocks.first(), "first")
        assertTrue(viewModel.canUndo.value)

        viewModel.undo()
        assertEquals("", (viewModel.blocks.first() as Block.TextBlock).text.value)
        assertTrue(viewModel.canRedo.value)

        viewModel.redo()
        assertEquals("first", (viewModel.blocks.first() as Block.TextBlock).text.value)
    }

    @Test
    fun focusFollowsTheBlockTheUserWorksOn() = runTest(dispatcher) {
        val viewModel = viewModel(SavedStateHandle())
        advanceUntilIdle()
        val block = viewModel.blocks.first()

        viewModel.onBlockFocused(block)
        assertEquals(block.id, viewModel.interactedBlockId.value)
        assertEquals(0, viewModel.indexOfBlock(block))

        viewModel.onBlockFocusLost(block)
        viewModel.onBlockInteraction(block)
        assertEquals(block.id, viewModel.interactedBlockId.value)
        assertEquals(-1, viewModel.indexOfBlock(Block.TextBlock()))
    }

    private fun storeNote(isTrash: Boolean = false) {
        val note = Note(
            id = NOTE_ID,
            title = "Stored",
            value = BlockConverter.blocksToJson(listOf(Block.TextBlock(MutableStateFlow("body")))),
            isTrash = isTrash,
        )
        repository.allNotesWithTags.value = listOf(NoteWithTag(note = note, tag = null))
    }

    private fun viewModel(handle: SavedStateHandle) = EditNoteViewModel(
        notesRepository = repository,
        savedStateHandle = handle,
        updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(repository),
        settingsRepository = settings,
        appScope = CoroutineScope(dispatcher),
        ioDispatcher = dispatcher,
        copyTitle = { "$it (Copy)" },
    )

    private companion object {
        const val NOTE_ID = 12L
        const val INSERTED_ID = 40L
        const val TAG_ID = 6L
        const val FONT_SIZE = 20
    }
}
