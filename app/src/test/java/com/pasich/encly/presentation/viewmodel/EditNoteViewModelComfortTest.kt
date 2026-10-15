package com.pasich.encly.presentation.viewmodel

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import com.pasich.encly.core.security.VaultLockEvents
import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.presentation.editor.state.FindState
import com.pasich.encly.presentation.editor.state.LineBreak
import com.pasich.encly.presentation.editor.state.NoteTemplate
import com.pasich.encly.presentation.editor.state.SlashCommand
import com.pasich.encly.presentation.editor.state.blocks
import com.pasich.encly.testutil.TestNotesRepository
import com.pasich.encly.testutil.TestSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The Writing Comfort features around the editor ViewModel: guards, the lock and what is saved. */
@OptIn(ExperimentalCoroutinesApi::class)
class EditNoteViewModelComfortTest {
    private val dispatcher = StandardTestDispatcher()
    private val settings = TestSettingsRepository()
    private val locked = MutableStateFlow(false)
    private val lockEvents = object : VaultLockEvents {
        override val locked = this@EditNoteViewModelComfortTest.locked
    }
    private val note = Note(
        id = NOTE_ID,
        title = "Cats",
        value = BlockConverter.blocksToJson(listOf(Block.TextBlock(MutableStateFlow("the cat sat")))),
    )
    private val repository = TestNotesRepository().apply {
        allNotesWithTags.value = listOf(NoteWithTag(note = note, tag = null))
        insertResult = INSERTED_ID
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(noteId: Long = NOTE_ID) = EditNoteViewModel(
        notesRepository = repository,
        savedStateHandle = SavedStateHandle(mapOf("idNote" to noteId)),
        updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(repository),
        settingsRepository = settings,
        appScope = CoroutineScope(dispatcher),
        ioDispatcher = dispatcher,
        copyTitle = { "$it (Copy)" },
        lockEvents = lockEvents,
    )

    private fun TestScope.collecting(viewModel: EditNoteViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.simpleEdit.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.templatesOffered.collect {} }
    }

    private val enter = LineBreak("", listOf("", ""), "")

    // --- find ---------------------------------------------------------------------------------------

    @Test
    fun openingTheNoteAndUsingFindOrSlashSavesNothing() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openFind()
        viewModel.onFindQueryChanged("cat")
        viewModel.findNext()
        viewModel.slashPicker.open(viewModel.blocks.first().id)
        viewModel.slashPicker.dismiss()
        viewModel.closeFind()
        advanceTimeBy(10_000)
        advanceUntilIdle()

        assertEquals(0, repository.updateCalls)
        assertEquals(0, repository.insertCalls)
    }

    @Test
    fun findFollowsTheTextWhileItIsOpen() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.openFind()
        viewModel.onFindQueryChanged("cat")
        assertEquals(1, viewModel.find.value.matches.size)

        viewModel.onBlockTextChanged(viewModel.blocks.first(), "cat cat")
        advanceUntilIdle()

        assertEquals(2, viewModel.find.value.matches.size)
    }

    @Test
    fun findWorksInALockedNoteBecauseItOnlyReads() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.toggleLockEditor()

        viewModel.openFind()
        viewModel.onFindQueryChanged("sat")

        assertEquals(1, viewModel.find.value.matches.size)
        assertEquals("the cat sat", (viewModel.blocks.single() as Block.TextBlock).text.value)
    }

    @Test
    fun theQueryAndMatchesAreGoneWhenTheVaultLocks() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.openFind()
        viewModel.onFindQueryChanged("cat")
        viewModel.slashPicker.open(viewModel.blocks.first().id)

        locked.value = true
        advanceUntilIdle()

        assertEquals(FindState(), viewModel.find.value)
        assertNull(viewModel.slashPicker.menu.value)
    }

    // --- slash ----------------------------------------------------------------------------------------

    @Test
    fun slashOpensInAnEmptyParagraphAndEnterPicksTheHighlightedCommand() = runTest(dispatcher) {
        val viewModel = viewModel(noteId = -1L)
        collecting(viewModel)
        advanceUntilIdle()
        val block = viewModel.blocks.single()

        viewModel.onSlashTyped(block, "/", started = true)
        viewModel.slashPicker.commit()
        viewModel.slashPicker.moveHighlight(1) // Heading 1

        assertEquals("", viewModel.onLineBreak(block, enter))

        assertEquals(BlockType.H1, (viewModel.blocks.single() as Block.HBlock).blockType)
        assertNull(viewModel.slashPicker.menu.value)
        viewModel.undo()
        assertTrue(viewModel.blocks.single() is Block.TextBlock)
    }

    @Test
    fun enterStillSplitsTheParagraphWhileTheMenuIsNotShown() = runTest(dispatcher) {
        val viewModel = viewModel(noteId = -1L)
        collecting(viewModel)
        advanceUntilIdle()
        val block = viewModel.blocks.single()

        viewModel.onSlashTyped(block, "/", started = true) // not committed: hidden

        viewModel.onLineBreak(block, enter)

        assertEquals(2, viewModel.blocks.size)
        assertNull(viewModel.slashPicker.menu.value)
    }

    @Test
    fun slashNeverOpensInSimpleEdit() = runTest(dispatcher) {
        settings.simpleEditFlow.value = true
        val viewModel = viewModel(noteId = -1L)
        collecting(viewModel)
        advanceUntilIdle()

        viewModel.onSlashTyped(viewModel.blocks.single(), "/", started = true)

        assertNull(viewModel.slashPicker.menu.value)
    }

    @Test
    fun slashDoesNotOpenInAReadOnlyNoteOrOnAnotherKindOfBlock() = runTest(dispatcher) {
        val viewModel = viewModel()
        collecting(viewModel)
        advanceUntilIdle()
        viewModel.toggleLockEditor()

        viewModel.onSlashTyped(viewModel.blocks.single(), "/", started = true)
        assertNull(viewModel.slashPicker.menu.value)

        viewModel.applySlash(viewModel.blocks.single(), SlashCommand.QUOTE)
        assertTrue(viewModel.blocks.single() is Block.TextBlock)
    }

    // --- paste in Simple Edit --------------------------------------------------------------------------

    @Test
    fun simpleEditKeepsAPastedTextAsPlainTextInTheBlock() = runTest(dispatcher) {
        settings.simpleEditFlow.value = true
        val viewModel = viewModel(noteId = -1L)
        collecting(viewModel)
        advanceUntilIdle()
        val block = viewModel.blocks.single()

        val kept = viewModel.onLineBreak(block, LineBreak("", listOf("# Title", "- item", "", "text"), ""))

        assertNull(kept) // the field keeps the text, markers and blank line included
        assertEquals(1, viewModel.blocks.size)
    }

    // --- templates -----------------------------------------------------------------------------------

    @Test
    fun templatesAreOfferedOnlyForABrandNewEmptyNote() = runTest(dispatcher) {
        val fresh = viewModel(noteId = -1L)
        collecting(fresh)
        advanceUntilIdle()
        assertTrue(fresh.templatesOffered.value)

        fresh.onBlockTextChanged(fresh.blocks.single(), "a")
        advanceUntilIdle()
        assertFalse(fresh.templatesOffered.value)

        fresh.undo()
        advanceUntilIdle()
        assertTrue(fresh.templatesOffered.value)

        val opened = viewModel(noteId = NOTE_ID)
        collecting(opened)
        advanceUntilIdle()
        assertFalse(opened.templatesOffered.value)
    }

    @Test
    fun templatesAreNotOfferedInSimpleEdit() = runTest(dispatcher) {
        settings.simpleEditFlow.value = true
        val viewModel = viewModel(noteId = -1L)
        collecting(viewModel)
        advanceUntilIdle()

        assertFalse(viewModel.templatesOffered.value)
    }

    @Test
    fun pickingATemplateFillsTheNoteAndHidesTheOffer() = runTest(dispatcher) {
        val viewModel = viewModel(noteId = -1L)
        collecting(viewModel)
        advanceUntilIdle()

        viewModel.applyTemplate(NoteTemplate.CHECKLIST.blocks { "To do" })
        Snapshot.sendApplyNotifications() // what Compose does for snapshotFlow in the app
        advanceUntilIdle()

        assertEquals(2, viewModel.blocks.size)
        assertEquals("To do", (viewModel.blocks[0] as Block.HBlock).text.value)
        assertFalse(viewModel.templatesOffered.value)
        viewModel.undo()
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()
        assertTrue(viewModel.templatesOffered.value)
    }

    @Test
    fun duplicateAndDeleteOfABlockAreOneUndoStepEach() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.duplicateBlock(viewModel.blocks.single())
        assertEquals(2, viewModel.blocks.size)
        viewModel.undo()
        assertEquals(1, viewModel.blocks.size)
        assertFalse(viewModel.canUndo.value)
    }

    private companion object {
        const val NOTE_ID = 12L
        const val INSERTED_ID = 40L
    }
}
