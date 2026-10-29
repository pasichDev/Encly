package com.pasich.encly.editor.state

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Where the cursor goes over rounds of undo and redo: the step's own change decides, never a stale position. */
class UndoCaretTest {
    private var now = 0L
    private val editor = BlockEditorState { now }

    private fun step() {
        now += BlockOperations.MERGE_WINDOW_MS + 1
    }

    private suspend fun undone(): FocusRequest {
        editor.undo()
        return editor.selection.requests.first()
    }

    private suspend fun redone(): FocusRequest {
        editor.redo()
        return editor.selection.requests.first()
    }

    @Test
    fun aParagraphKeepsTheCursorAtEachEditAcrossThreeRoundsOfUndoAndRedo() = runTest {
        val block = Block.TextBlock(MutableStateFlow("hello world"))
        editor.load(listOf(block))
        editor.selection.onFocused(block.id)
        // X in the middle, Y at the start, ! at the end.
        listOf("helloX world", "YhelloX world", "YhelloX world!").forEach {
            step()
            editor.changeValue(block.id, block.text, it, mergeable = true)
        }

        repeat(3) { round ->
            assertEquals(13, undone().caret, "round $round") // before the "!"
            assertEquals(0, undone().caret, "round $round") // before the "Y"
            assertEquals(5, undone().caret, "round $round") // before the "X"
            assertEquals(6, redone().caret, "round $round") // after the "X"
            assertEquals(1, redone().caret, "round $round") // after the "Y"
            assertEquals(14, redone().caret, "round $round") // after the "!"
        }
    }

    @Test
    fun undoingAnEnterInAListGoesBackToTheEndOfTheItemNeverToTheFirstItem() = runTest {
        val a = ItemListBlock("alpha")
        val b = ItemListBlock("beta")
        val list = Block.ListBlock(MutableStateFlow(listOf(a, b)), BlockType.LIST_BULLET)
        editor.load(listOf(list))
        editor.selection.onFocused(list.id)
        val fresh = ItemListBlock("")
        // Enter at the end of "beta", then typing in the new item, then another Enter.
        step()
        editor.changeValue(list.id, list.items, listOf(a, b, fresh), mergeable = false)
        step()
        editor.changeValue(list.id, list.items, listOf(a, b, fresh.copy(value = "gamma")), mergeable = true)
        val third = ItemListBlock("")
        step()
        editor.changeValue(list.id, list.items, listOf(a, b, fresh.copy(value = "gamma"), third), mergeable = false)

        repeat(2) {
            val first = undone() // the second Enter: end of "gamma"
            assertEquals(FocusRequest(list.id, caret = 5, itemId = fresh.id), first)
            val typing = undone() // the typing: where it began
            assertEquals(FocusRequest(list.id, caret = 0, itemId = fresh.id), typing)
            val enter = undone() // the first Enter: end of "beta"
            assertEquals(FocusRequest(list.id, caret = 4, itemId = b.id), enter)
            assertEquals(FocusRequest(list.id, caret = 0, itemId = fresh.id), redone()) // the item is back, empty
            assertEquals(FocusRequest(list.id, caret = 5, itemId = fresh.id), redone()) // typed text, at its end
            assertEquals(FocusRequest(list.id, caret = 0, itemId = third.id), redone()) // the new item
        }
    }

    @Test
    fun anEditInAnotherItemMovesTheCursorThereNotToTheItemLastFocused() = runTest {
        val a = ItemListBlock("one")
        val b = ItemListBlock("two")
        val list = Block.ListBlock(MutableStateFlow(listOf(a, b)), BlockType.LIST_NUMBER)
        editor.load(listOf(list))
        editor.selection.onFocused(list.id)
        editor.selection.onItemCaret(list.id, b.id, 1) // the cursor has since moved to "two"
        step()
        editor.changeValue(list.id, list.items, listOf(a.copy(value = "oneX"), b), mergeable = true)

        assertEquals(FocusRequest(list.id, caret = 3, itemId = a.id), undone())
        assertEquals(FocusRequest(list.id, caret = 4, itemId = a.id), redone())
    }

    @Test
    fun anItemMergedByBackspaceComesBackAtItsStartAndGoesAwayAtTheJoin() = runTest {
        val a = ItemListBlock("ab")
        val b = ItemListBlock("cd")
        val list = Block.ListBlock(MutableStateFlow(listOf(a, b)), BlockType.LIST_CHECK)
        editor.load(listOf(list))
        editor.selection.onFocused(list.id)
        step()
        editor.changeValue(list.id, list.items, listOf(a.copy(value = "abcd")), mergeable = false)

        assertEquals(FocusRequest(list.id, caret = 0, itemId = b.id), undone())
        assertEquals(FocusRequest(list.id, caret = 2, itemId = a.id), redone())
    }

    @Test
    fun tickingABoxLeavesTheCursorAlone() = runTest {
        val a = ItemListBlock("milk")
        val list = Block.ListBlock(MutableStateFlow(listOf(a)), BlockType.LIST_CHECK)
        editor.load(listOf(list))
        editor.selection.onFocused(list.id)
        editor.selection.onItemCaret(list.id, a.id, 2)
        step()
        editor.changeValue(list.id, list.items, listOf(a.copy(isCheck = true)), mergeable = false)

        assertEquals(FocusRequest(list.id, caret = 2, itemId = a.id), undone())
    }

    @Test
    fun anItemThatIsGoneIsNeverAskedForByTheFallback() = runTest {
        val a = ItemListBlock("one")
        val gone = ItemListBlock("two")
        val list = Block.ListBlock(MutableStateFlow(listOf(a)), BlockType.LIST_BULLET)
        editor.load(listOf(list))
        editor.selection.onFocused(list.id)
        editor.selection.onItemCaret(list.id, gone.id, 3) // a stale record of an item that no longer exists
        step()
        editor.addBlock(Block.TextBlock(MutableStateFlow("x")))
        editor.selection.onFocused(list.id)
        editor.undo()

        val request = editor.selection.requests.first()

        assertEquals(null, request.itemId)
    }

    private fun enter(text: String, at: Int) = LineBreak(text.take(at), listOf("", ""), text.drop(at))

    @Test
    fun anEnterInAParagraphUndoesToTheBreakAndRedoesIntoTheNewParagraph() = runTest {
        val block = Block.TextBlock(MutableStateFlow("abcdef"))
        editor.load(listOf(block))
        editor.selection.onFocused(block.id)
        step()
        editor.breakLine(block.id, enter("abcdef", 3))
        val next = editor.blocks[1]

        repeat(2) {
            assertEquals(FocusRequest(block.id, caret = 3), undone())
            assertEquals(FocusRequest(next.id, caret = 0), redone())
        }
    }

    @Test
    fun aParagraphJoinedToAHeadingRedoesToTheJoinNotTheEnd() = runTest {
        val heading = Block.HBlock(MutableStateFlow("Title"), blockType = BlockType.H2)
        val body = Block.TextBlock(MutableStateFlow("body"))
        editor.load(listOf(heading, body))
        editor.selection.onFocused(body.id)
        step()
        editor.backspaceAtStart(body.id)

        repeat(2) {
            assertEquals(FocusRequest(body.id, caret = 0), undone())
            assertEquals(FocusRequest(heading.id, caret = 5), redone())
        }
    }

    @Test
    fun anItemThatLeftTheListRedoesIntoTheParagraphItBecame() = runTest {
        val a = ItemListBlock("one")
        val b = ItemListBlock("two")
        val list = Block.ListBlock(MutableStateFlow(listOf(a, b)), BlockType.LIST_BULLET)
        editor.load(listOf(list))
        editor.selection.onFocused(list.id)
        step()
        editor.listBackspaceAtStart(list.id, a.id)
        val paragraph = editor.blocks[0]

        repeat(2) {
            assertEquals(FocusRequest(list.id, caret = 0, itemId = a.id), undone())
            assertEquals(FocusRequest(paragraph.id, caret = 0), redone())
        }
    }
}
