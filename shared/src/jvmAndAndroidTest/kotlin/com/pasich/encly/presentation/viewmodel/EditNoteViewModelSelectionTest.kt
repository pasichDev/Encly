package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTags
import com.pasich.encly.domain.usecase.note.UpdateNoteArchiveStatusUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.editor.codec.BlockConverter
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.state.TextPoint
import com.pasich.encly.platform.SensitiveClipboard
import com.pasich.encly.presentation.screen.editnote.copyPickedBlocks
import com.pasich.encly.presentation.screen.editnote.cutPickedBlocks
import com.pasich.encly.presentation.screen.editnote.selectedBlocksText
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

/** Picking whole blocks in a note: who may, and what copy and cut put on the clipboard. */
@OptIn(ExperimentalCoroutinesApi::class)
class EditNoteViewModelSelectionTest {
    private val dispatcher = StandardTestDispatcher()
    private val settings = TestSettingsRepository()
    private val note = Note(
        id = NOTE_ID,
        title = "Cats",
        value = BlockConverter.blocksToJson(
            listOf(
                Block.HBlock(MutableStateFlow("Title"), BlockType.H1),
                Block.TextBlock(MutableStateFlow("the cat sat")),
                Block.QuoteBlock(MutableStateFlow("on the mat")),
            ),
        ),
    )
    private val repository = TestNotesRepository().apply {
        allNotesWithTags.value = listOf(NoteWithTags(note, emptyList()))
    }
    private val clipboard = FakeClipboard()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeClipboard : SensitiveClipboard {
        var copies = mutableListOf<Pair<String, String>>()
        var accepts = true
        override var systemConfirmsCopy = false

        override fun copy(label: String, text: String): Boolean = accepts.also { if (it) copies += label to text }

        override fun clear() = Unit
    }

    private fun viewModel(trash: Boolean = false) = EditNoteViewModel(
        notesRepository = repository,
        savedStateHandle = SavedStateHandle(mapOf("idNote" to NOTE_ID, "isReadTrashOnly" to trash)),
        updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(repository),
        updateNoteArchiveStatusUseCase = UpdateNoteArchiveStatusUseCase(repository),
        settingsRepository = settings,
        appScope = CoroutineScope(dispatcher),
        ioDispatcher = dispatcher,
        copyTitle = { "$it (Copy)" },
    )

    private fun TestScope.opened(trash: Boolean = false): EditNoteViewModel {
        val viewModel = viewModel(trash)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.simpleEdit.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    // --- who may pick ----------------------------------------------------------------------------------

    @Test
    fun selectAllPicksTheWholeNote() = runTest(dispatcher) {
        val viewModel = opened()

        assertTrue(viewModel.selectAllBlocks())

        assertEquals(3, viewModel.selectedBlocks.count)
    }

    @Test
    fun selectAllTextSelectsTheWholeNotesTextExceptInSimpleEditing() = runTest(dispatcher) {
        val viewModel = opened()

        assertTrue(viewModel.selectAllText())
        assertTrue(viewModel.textRange.isCrossField)
        assertEquals(
            "Title\n\nthe cat sat\n\non the mat",
            viewModel.textRangeBlocks().let {
                selectedBlocksText(it, false)
            },
        )

        settings.simpleEditFlow.value = true
        val simple = opened()
        assertFalse(simple.selectAllText())
    }

    @Test
    fun simpleEditingHasNoBlocksToPick() = runTest(dispatcher) {
        settings.simpleEditFlow.value = true
        val viewModel = opened()

        assertFalse(viewModel.selectAllBlocks())

        assertFalse(viewModel.selectedBlocks.isActive)
    }

    @Test
    fun aLockedNoteCanBePickedButNotCutOrDeleted() = runTest(dispatcher) {
        val viewModel = opened()
        viewModel.toggleLockEditor()
        assertTrue(viewModel.selectAllBlocks())
        assertFalse(viewModel.canEditBlocks)

        assertFalse(viewModel.deleteSelectedBlocks())
        assertNull(cutPickedBlocks(clipboard, viewModel, "label", "Copied"))

        assertEquals(3, viewModel.blocks.size)
        assertTrue(clipboard.copies.isEmpty())
        assertTrue(viewModel.selectedBlocks.isActive)
    }

    @Test
    fun aNoteInTheTrashCanBeCopiedFrom() = runTest(dispatcher) {
        val viewModel = opened(trash = true)
        viewModel.selectAllBlocks()

        assertEquals("Copied", copyPickedBlocks(clipboard, viewModel, markdown = false, "label", "Copied"))

        assertEquals("Title\n\nthe cat sat\n\non the mat", clipboard.copies.single().second)
    }

    // --- deleting --------------------------------------------------------------------------------------

    @Test
    fun deletingPickedBlocksIsOneUndoStep() = runTest(dispatcher) {
        val viewModel = opened()
        viewModel.selectedBlocks.select(viewModel.blocks[0].id)
        viewModel.selectedBlocks.toggle(viewModel.blocks[2].id)

        assertTrue(viewModel.deleteSelectedBlocks())
        assertEquals(1, viewModel.blocks.size)

        viewModel.undo()
        assertEquals(3, viewModel.blocks.size)
    }

    // --- copy and cut ----------------------------------------------------------------------------------

    @Test
    fun theTextOfPickedBlocksIsMarkdownOrPlain() {
        val blocks = listOf(
            Block.HBlock(MutableStateFlow("Title"), BlockType.H2),
            Block.TextBlock(MutableStateFlow("body")),
        )

        assertEquals("## Title\n\nbody", selectedBlocksText(blocks, markdown = true))
        assertEquals("Title\n\nbody", selectedBlocksText(blocks, markdown = false))
    }

    @Test
    fun copyingGoesThroughTheSensitiveClipboardAndEndsTheMode() = runTest(dispatcher) {
        val viewModel = opened()
        viewModel.selectAllBlocks()

        val said = copyPickedBlocks(clipboard, viewModel, markdown = true, "label", "Copied as Markdown")

        assertEquals("Copied as Markdown", said)
        assertEquals("label" to "# Title\n\nthe cat sat\n\n> on the mat", clipboard.copies.single())
        assertFalse(viewModel.selectedBlocks.isActive)
        assertEquals(3, viewModel.blocks.size)
    }

    @Test
    fun noConfirmationWhereTheSystemSaysItItself() = runTest(dispatcher) {
        clipboard.systemConfirmsCopy = true
        val viewModel = opened()
        viewModel.selectAllBlocks()

        assertNull(copyPickedBlocks(clipboard, viewModel, markdown = false, "label", "Copied"))

        assertEquals(1, clipboard.copies.size)
    }

    @Test
    fun aCopyThatFailedKeepsTheSelection() = runTest(dispatcher) {
        clipboard.accepts = false
        val viewModel = opened()
        viewModel.selectAllBlocks()

        assertNull(copyPickedBlocks(clipboard, viewModel, markdown = false, "label", "Copied"))

        assertTrue(viewModel.selectedBlocks.isActive)
    }

    @Test
    fun nothingPickedCopiesNothing() = runTest(dispatcher) {
        val viewModel = opened()

        assertNull(copyPickedBlocks(clipboard, viewModel, markdown = false, "label", "Copied"))

        assertTrue(clipboard.copies.isEmpty())
    }

    @Test
    fun cuttingCopiesAsMarkdownAndThenRemovesTheBlocksInOneStep() = runTest(dispatcher) {
        val viewModel = opened()
        viewModel.selectedBlocks.select(viewModel.blocks[1].id)
        viewModel.selectedBlocks.toggle(viewModel.blocks[2].id)

        assertEquals("Copied", cutPickedBlocks(clipboard, viewModel, "label", "Copied"))

        assertEquals("the cat sat\n\n> on the mat", clipboard.copies.single().second)
        assertEquals(1, viewModel.blocks.size)
        viewModel.undo()
        assertEquals(3, viewModel.blocks.size)
    }

    @Test
    fun pastingBackExactlyWhatWasCutInsertsTheTextNotItsEscapedMarkdown() = runTest(dispatcher) {
        val viewModel = opened()
        viewModel.onBlockTextChanged(viewModel.blocks[1], "price*2")
        viewModel.selectedBlocks.select(viewModel.blocks[1].id)
        cutPickedBlocks(clipboard, viewModel, "label", "Copied")
        val onClipboard = clipboard.copies.single().second
        assertEquals("price\\*2", onClipboard)
        val target = viewModel.blocks.last()

        assertNull(viewModel.onPasteCopied(target, null, "", "something else", ""))
        assertEquals("price*2on the mat", viewModel.onPasteCopied(target, null, "", onClipboard, "on the mat"))

        viewModel.toggleLockEditor()
        assertNull(viewModel.onPasteCopied(target, null, "", onClipboard, "on the mat"))
    }

    @Test
    fun aCopyWithNothingInItForgetsTheEarlierOne() = runTest(dispatcher) {
        val viewModel = opened()
        viewModel.selectedBlocks.select(viewModel.blocks[1].id)
        copyPickedBlocks(clipboard, viewModel, markdown = false, "label", "Copied")

        viewModel.rememberCopied("x", emptyList())

        assertNull(viewModel.onPasteCopied(viewModel.blocks[2], null, "", "the cat sat", ""))
    }

    @Test
    fun aCutThatCouldNotCopyRemovesNothing() = runTest(dispatcher) {
        clipboard.accepts = false
        val viewModel = opened()
        viewModel.selectAllBlocks()

        assertNull(cutPickedBlocks(clipboard, viewModel, "label", "Copied"))

        assertEquals(3, viewModel.blocks.size)
    }

    @Test
    fun cuttingAsksNothingOfASystemThatConfirmsItself() = runTest(dispatcher) {
        clipboard.systemConfirmsCopy = true
        val viewModel = opened()
        viewModel.selectAllBlocks()

        assertNull(cutPickedBlocks(clipboard, viewModel, "label", "Copied"))

        assertEquals(1, viewModel.blocks.size)
    }

    @Test
    fun aTextSelectionAcrossBlocksIsReplacedOnlyWhereTheNoteCanChange() = runTest(dispatcher) {
        val viewModel = opened()
        val ends = viewModel.blocks.map { it.id }
        viewModel.textRange.select(TextPoint(ends[0], 2), TextPoint(ends[2], 3))

        assertTrue(viewModel.replaceTextRange("-"))
        assertEquals(1, viewModel.blocks.size)

        viewModel.undo()
        viewModel.toggleLockEditor()
        viewModel.textRange.select(TextPoint(ends[0], 2), TextPoint(ends[2], 3))
        assertFalse(viewModel.replaceTextRange("-"))
        assertEquals(3, viewModel.blocks.size)
    }

    private companion object {
        const val NOTE_ID = 91L
    }
}
