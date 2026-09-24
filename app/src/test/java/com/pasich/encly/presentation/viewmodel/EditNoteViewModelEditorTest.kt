package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.domain.model.FontStyleType
import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.domain.model.LinkDataBlock
import com.pasich.encly.domain.repository.SettingsRepository
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.presentation.editor.persistence.AUTOSAVE_DEBOUNCE_MS
import com.pasich.encly.presentation.editor.state.BlockRemoveAction
import com.pasich.encly.presentation.editor.state.FocusRequest
import com.pasich.encly.testutil.TestNotesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Regression tests for the note editor: data-loss races around saving, index/identity bugs
 * in the block list, and undo/redo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditNoteViewModelEditorTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- saving races ------------------------------------------------------------------------

    @Test
    fun autosaveQueuedBeforeTrashCannotBringTheNoteBack() = runTest {
        val repository = repositoryWith(storedNote(text("original")))
        val viewModel = createViewModel(repository, noteId = NOTE_ID)
        advanceUntilIdle()

        viewModel.onBlockTextChanged(viewModel.blocks[0], "edited")
        assertTrue(viewModel.noteMoveToTrash())
        advanceUntilIdle() // the debounced autosave would fire here

        assertTrue(repository.updatedNotes.last().isTrash)
        assertTrue(viewModel.state.value.note.isTrash)
    }

    @Test
    fun autosaveQueuedBeforeDiscardCannotOverwriteTheRestoredNote() = runTest {
        val repository = repositoryWith(storedNote(text("original")))
        val viewModel = createViewModel(repository, noteId = NOTE_ID)
        advanceUntilIdle()

        viewModel.onBlockTextChanged(viewModel.blocks[0], "edited")
        assertTrue(viewModel.discardChanges())
        viewModel.saveForBackground()
        advanceUntilIdle()

        assertEquals(1, repository.updateCalls)
        assertEquals(storedNote(text("original")).value, repository.updatedNotes.single().value)
    }

    @Test
    fun leavingWhileTheNoteLoadsDoesNotInsertADuplicate() = runTest {
        val repository = repositoryWith(storedNote(text("body")))
        val viewModel = createViewModel(repository, noteId = NOTE_ID)

        runCurrent() // start loading, without letting any timer run
        assertTrue(viewModel.saveNote())
        viewModel.saveForBackground()
        advanceUntilIdle()

        assertEquals(0, repository.insertCalls)
        assertEquals(NOTE_ID, viewModel.state.value.note.id)
    }

    @Test
    fun saveBeforeLoadingStartsIsANoOp() = runTest {
        val repository = repositoryWith(storedNote(text("body")))
        val viewModel = createViewModel(repository, noteId = NOTE_ID)

        assertTrue(viewModel.saveNote())

        assertEquals(0, repository.insertCalls)
        assertEquals(0, repository.updateCalls)
    }

    @Test
    fun firstEditOfANewNoteIsAutosaved() = runTest {
        val repository = TestNotesRepository().apply { insertResult = INSERTED_ID }
        val viewModel = createViewModel(repository)

        viewModel.updateTitle("typed right away")
        advanceUntilIdle()

        assertEquals(1, repository.insertCalls)
    }

    @Test
    fun firstEditOfAnOpenedNoteIsAutosavedButOpeningAloneSavesNothing() = runTest {
        val repository = repositoryWith(storedNote(text("body")))
        val viewModel = createViewModel(repository, noteId = NOTE_ID)
        advanceUntilIdle()
        assertEquals(0, repository.updateCalls)

        viewModel.onBlockTextChanged(viewModel.blocks[0], "body, edited")
        advanceUntilIdle()

        assertEquals(1, repository.updateCalls)
    }

    @Test
    fun titleEditOfASeparatorOnlyNoteIsAutosaved() = runTest {
        val repository = repositoryWith(storedNote(Block.SeparatorBlock()))
        val viewModel = createViewModel(repository, noteId = NOTE_ID)
        advanceUntilIdle()

        viewModel.updateTitle("renamed")
        advanceUntilIdle()

        assertEquals("renamed", repository.updatedNotes.single().title)
    }

    @Test
    fun whitespaceOnlyNewNoteIsNotInserted() = runTest {
        val repository = TestNotesRepository()
        val viewModel = createViewModel(repository)

        viewModel.onBlockTextChanged(viewModel.blocks[0], "   \n ")
        assertTrue(viewModel.saveNote())

        assertEquals(0, repository.insertCalls)
    }

    @Test
    fun copyOfAnUntaggedNoteStaysUntagged() = runTest {
        val repository = repositoryWith(storedNote(text("body")))
        val viewModel = createViewModel(repository, copySource = NOTE_ID)
        advanceUntilIdle()

        assertEquals(-1L, viewModel.state.value.note.id)
        assertNull(viewModel.state.value.note.tagId)
    }

    // --- block identity and indices -------------------------------------------------------

    @Test
    fun deletingTheSecondOfTwoSeparatorsDeletesThatOne() = runTest {
        val repository = repositoryWith(
            storedNote(text("a"), Block.SeparatorBlock(), text("b"), Block.SeparatorBlock()),
        )
        val viewModel = createViewModel(repository, noteId = NOTE_ID)
        advanceUntilIdle()
        val firstSeparator = viewModel.blocks[1]

        viewModel.removeBlock(viewModel.blocks[3], BlockRemoveAction.REMOVE)

        assertEquals(3, viewModel.blocks.size)
        assertSame(firstSeparator, viewModel.blocks[1])
        assertTrue(viewModel.blocks.last() is Block.TextBlock)
    }

    @Test
    fun undoAfterAddingABlockKeepsTheInteractionIndexInsideTheList() = runTest {
        val viewModel = createViewModel(TestNotesRepository())
        viewModel.onBlockTextChanged(viewModel.blocks[0], "text")
        viewModel.addBlockAfter(0, BlockType.H1)
        assertEquals(1, viewModel.interactedIndex)

        viewModel.undo()

        assertEquals(1, viewModel.blocks.size)
        assertTrue(viewModel.interactedIndex in viewModel.blocks.indices)
        assertEquals(1, viewModel.getMoveBlockState())
    }

    @Test
    fun undoingABlockAddedOverTheEmptyFirstBlockNeverEmptiesTheNote() = runTest {
        val viewModel = createViewModel(TestNotesRepository())

        viewModel.addBlock(BlockType.H1)
        repeat(3) { viewModel.undo() }

        assertEquals(1, viewModel.blocks.size)
        assertTrue(viewModel.blocks.single() is Block.TextBlock)
    }

    @Test
    fun addedBlocksGoAfterTheFocusedBlockAndTextAddsNoHiddenNewline() = runTest {
        val repository = repositoryWith(storedNote(text("a"), text("b"), text("c")))
        val viewModel = createViewModel(repository, noteId = NOTE_ID)
        advanceUntilIdle()
        viewModel.onBlockFocused(viewModel.blocks[0])

        viewModel.addBlock(BlockType.TEXT)

        assertEquals(4, viewModel.blocks.size)
        assertEquals("", (viewModel.blocks[1] as Block.TextBlock).text.value)
        assertEquals("c", (viewModel.blocks[3] as Block.TextBlock).text.value)
    }

    @Test
    fun aLockedEditorCannotGainBlocks() = runTest {
        val repository = repositoryWith(storedNote(Block.SeparatorBlock()))
        val viewModel = createViewModel(repository, noteId = NOTE_ID)
        advanceUntilIdle()
        viewModel.toggleLockEditor()

        viewModel.addBlockToEnd()
        viewModel.addBlock(BlockType.QUOTE)

        assertEquals(1, viewModel.blocks.size)
    }

    @Test
    fun replacingABlockFocusesTheNewFieldEvenAtTheSameIndex() = runTest {
        val viewModel = createViewModel(TestNotesRepository())
        viewModel.onBlockFocused(viewModel.blocks[0])
        val replacement = Block.TextBlock()

        viewModel.replaceBlock(0, replacement)

        assertEquals(FocusRequest(replacement.id), viewModel.focusRequests.first())
    }

    // --- undo / redo --------------------------------------------------------------------------

    @Test
    fun undoAfterUndoAndRetypeRestoresTheModelTextNotAStaleCopy() = runTest {
        val viewModel = createViewModel(TestNotesRepository())
        val block = viewModel.blocks[0] as Block.TextBlock
        viewModel.onBlockTextChanged(block, "hello")
        viewModel.undo()

        // The next edit starts from the undone text; undoing it must not bring "hello" back.
        viewModel.onBlockTextChanged(block, "x")
        viewModel.undo()

        assertEquals("", block.text.value)
        viewModel.redo()
        assertEquals("x", block.text.value)
    }

    @Test
    fun textUndoTargetsItsOwnBlockAfterABlockIsInsertedBeforeIt() = runTest {
        val repository = repositoryWith(storedNote(text("hello"), text("world")))
        val viewModel = createViewModel(repository, noteId = NOTE_ID)
        advanceUntilIdle()
        val world = viewModel.blocks[1] as Block.TextBlock

        viewModel.addBlockAfter(0, BlockType.TEXT)
        viewModel.onBlockTextChanged(viewModel.blocks[1], "a")
        viewModel.undo()

        assertEquals("", (viewModel.blocks[1] as Block.TextBlock).text.value)
        assertEquals("world", world.text.value)
    }

    @Test
    fun checklistEditsAreUndoable() = runTest {
        val repository = repositoryWith(
            storedNote(Block.ListBlock(MutableStateFlow(listOf(ItemListBlock("milk"))), BlockType.LIST_CHECK)),
        )
        val viewModel = createViewModel(repository, noteId = NOTE_ID)
        advanceUntilIdle()
        val list = viewModel.blocks[0] as Block.ListBlock

        viewModel.onListItemsChanged(list, listOf(list.items.value[0].copy(isCheck = true)), mergeable = false)
        assertTrue(viewModel.canUndo.value)
        viewModel.undo()

        assertFalse(list.items.value.single().isCheck)
    }

    // --- links -------------------------------------------------------------------------------

    @Test
    fun editingALinkKeepsTheSavedLinkThroughAutosaveAndExit() = runTest {
        val repository = repositoryWith(storedNote(link(SAVED_URL)))
        val viewModel = createViewModel(repository, noteId = NOTE_ID)
        advanceUntilIdle()
        val linkBlock = viewModel.blocks.single() as Block.LinkBlock

        viewModel.editLink(linkBlock)
        assertTrue(linkBlock.id in viewModel.editingLinkIds.value)
        advanceTimeBy(AUTOSAVE_DEBOUNCE_MS + 1)
        advanceUntilIdle()
        assertTrue(viewModel.saveNote()) // Back without committing a new address

        assertEquals(SAVED_URL, linkBlock.block.value.url)
        assertTrue(repository.updatedNotes.isNotEmpty())
        assertTrue(repository.updatedNotes.all { SAVED_URL in it.value })
    }

    @Test
    fun committingAnEditedLinkStoresTheNewAddressAndEndsEditing() = runTest {
        val repository = repositoryWith(storedNote(link(SAVED_URL)))
        val viewModel = createViewModel(repository, noteId = NOTE_ID)
        advanceUntilIdle()
        val linkBlock = viewModel.blocks.single() as Block.LinkBlock

        viewModel.editLink(linkBlock)
        viewModel.onLinkChanged(linkBlock, LinkDataBlock(title = "example.org", url = "https://example.org"))
        assertTrue(viewModel.saveNote())

        assertTrue(viewModel.editingLinkIds.value.isEmpty())
        assertTrue("https://example.org" in repository.updatedNotes.last().value)
    }

    @Test
    fun aLockedEditorCannotEditALink() = runTest {
        val repository = repositoryWith(storedNote(link(SAVED_URL)))
        val viewModel = createViewModel(repository, noteId = NOTE_ID)
        advanceUntilIdle()
        viewModel.toggleLockEditor()

        viewModel.editLink(viewModel.blocks.single() as Block.LinkBlock)

        assertTrue(viewModel.editingLinkIds.value.isEmpty())
    }

    // --- read-only ------------------------------------------------------------------------------

    @Test
    fun aLockedEditorCannotDeleteABlock() = runTest {
        val repository = repositoryWith(storedNote(text("a"), Block.SeparatorBlock(), text("b")))
        val viewModel = createViewModel(repository, noteId = NOTE_ID)
        advanceUntilIdle()
        viewModel.toggleLockEditor()

        viewModel.removeBlock(viewModel.blocks[1], BlockRemoveAction.REMOVE)

        assertEquals(3, viewModel.blocks.size)
    }

    @Test
    fun aTrashedNoteIsRestoredWithEveryBlockEvenAfterADeleteAttempt() = runTest {
        val stored = storedNote(text("a"), Block.SeparatorBlock(), text("b")).copy(isTrash = true)
        val repository = repositoryWith(stored)
        val viewModel = createViewModel(repository, noteId = NOTE_ID, isReadTrashOnly = true)
        advanceUntilIdle()

        viewModel.removeBlock(viewModel.blocks[1], BlockRemoveAction.REMOVE)
        assertTrue(viewModel.noteRestore())

        assertEquals(stored.value, repository.updatedNotes.single().value)
    }

    @Test
    fun anUnreadableNoteStaysLockedAndCannotBeEdited() = runTest {
        val broken = Note(id = NOTE_ID, title = "broken", value = "{not-json")
        val viewModel = createViewModel(repositoryWith(broken), noteId = NOTE_ID)
        advanceUntilIdle()
        val blocksBefore = viewModel.blocks.toList()

        assertTrue(viewModel.lockEditor.value)
        viewModel.toggleLockEditor()
        assertTrue(viewModel.lockEditor.value)

        viewModel.addBlock(BlockType.QUOTE)
        viewModel.onBlockTextChanged(viewModel.blocks[0], "typed")

        assertEquals(blocksBefore, viewModel.blocks)
        assertEquals("", (viewModel.blocks[0] as Block.TextBlock).text.value)
    }

    @Test
    fun aCopyRemembersItsIdOnceStoredSoARestoredScreenOpensIt() = runTest {
        val repository = repositoryWith(storedNote(text("body")))
        val handle = SavedStateHandle(mapOf("idNote" to -1L, "copySource" to NOTE_ID))
        val viewModel = createViewModel(repository, handle = handle)
        advanceUntilIdle()

        viewModel.updateTitle("draft")
        assertTrue(viewModel.saveNote())
        advanceUntilIdle()

        assertEquals(INSERTED_ID, handle.get<Long>("idNote"))
        assertEquals(-1L, handle.get<Long>("copySource"))
    }

    @Test
    fun choosingNoTagStoresNoTag() = runTest {
        val repository = repositoryWith(storedNote(text("body")))
        val viewModel = createViewModel(repository, noteId = NOTE_ID)
        advanceUntilIdle()

        viewModel.updateTagNote(TAG_ID)
        assertEquals(TAG_ID, viewModel.state.value.note.tagId)
        viewModel.updateTagNote(0L)

        assertNull(viewModel.state.value.note.tagId)
    }

    @Test
    fun onlyABrandNewNoteIsNew() = runTest {
        assertTrue(createViewModel(TestNotesRepository()).isNewNote)
        assertFalse(createViewModel(repositoryWith(storedNote(text("b"))), noteId = NOTE_ID).isNewNote)
        assertFalse(createViewModel(repositoryWith(storedNote(text("b"))), copySource = NOTE_ID).isNewNote)
    }

    @Test
    fun aToolAppliesToTheFocusedBlockButNotInALockedEditor() = runTest {
        val repository = repositoryWith(storedNote(text("a")))
        val viewModel = createViewModel(repository, noteId = NOTE_ID)
        advanceUntilIdle()
        viewModel.onBlockFocused(viewModel.blocks[0])

        viewModel.toggleLockEditor()
        viewModel.applyTool(BlockType.QUOTE)
        assertTrue(viewModel.blocks.single() is Block.TextBlock)

        viewModel.toggleLockEditor()
        viewModel.applyTool(BlockType.QUOTE)
        assertEquals("a", (viewModel.blocks.single() as Block.QuoteBlock).text.value)
    }

    // --- toolbar -------------------------------------------------------------------------------

    @Test
    fun toolbarMovesTheBlockTheUserWorksOn() = runTest {
        val repository = repositoryWith(storedNote(text("a"), text("b"), text("c")))
        val viewModel = createViewModel(repository, noteId = NOTE_ID)
        advanceUntilIdle()
        viewModel.onBlockInteraction(viewModel.blocks[1])

        assertTrue(viewModel.canMoveBlock(up = true))
        viewModel.moveBlock(up = true)
        assertEquals(listOf("b", "a", "c"), texts(viewModel))

        // "b" is now first: it cannot go further up, but can go down again.
        assertFalse(viewModel.canMoveBlock(up = true))
        assertTrue(viewModel.canMoveBlock(up = false))
    }

    @Test
    fun toolbarDeleteRemovesTheBlockTheUserWorksOnButNeverTheLastOne() = runTest {
        val repository = repositoryWith(storedNote(text("a"), text("b")))
        val viewModel = createViewModel(repository, noteId = NOTE_ID)
        advanceUntilIdle()
        viewModel.onBlockInteraction(viewModel.blocks[1])

        viewModel.removeInteractedBlock()
        assertEquals(listOf("a"), texts(viewModel))

        assertFalse(viewModel.canRemoveInteractedBlock())
        viewModel.removeInteractedBlock()
        assertEquals(listOf("a"), texts(viewModel))
    }

    private fun texts(viewModel: EditNoteViewModel): List<String> = viewModel.blocks.map {
        (it as Block.TextBlock).text.value
    }

    private fun repositoryWith(note: Note) = TestNotesRepository().apply {
        allNotesWithTags.value = listOf(NoteWithTag(note = note, tag = null))
        insertResult = INSERTED_ID
    }

    private fun storedNote(vararg blocks: Block) = Note(
        id = NOTE_ID,
        title = "title",
        value = BlockConverter.blocksToJson(blocks.toList()),
    )

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    private fun link(url: String) = Block.LinkBlock(MutableStateFlow(LinkDataBlock(title = "example.com", url = url)))

    private fun createViewModel(
        repository: TestNotesRepository,
        noteId: Long = -1L,
        copySource: Long = -1L,
        isReadTrashOnly: Boolean = false,
        handle: SavedStateHandle = SavedStateHandle(
            mapOf("idNote" to noteId, "copySource" to copySource, "isReadTrashOnly" to isReadTrashOnly),
        ),
    ): EditNoteViewModel {
        val settingsRepository = mock(SettingsRepository::class.java)
        `when`(settingsRepository.fontSizeFlow).thenReturn(flowOf(DEFAULT_FONT_SIZE))
        `when`(settingsRepository.fontStyleFlow).thenReturn(flowOf(FontStyleType.MODERN_SIMPLE))
        `when`(settingsRepository.simpleEditFlow).thenReturn(flowOf(false))

        return EditNoteViewModel(
            notesRepository = repository,
            savedStateHandle = handle,
            updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(repository),
            settingsRepository = settingsRepository,
            appScope = CoroutineScope(dispatcher),
            ioDispatcher = dispatcher,
            copyTitle = { "$it (Copy)" },
        )
    }

    private companion object {
        const val NOTE_ID = 91L
        const val INSERTED_ID = 73L
        const val DEFAULT_FONT_SIZE = 16
        const val SAVED_URL = "https://example.com/saved"
        const val TAG_ID = 5L
    }
}
