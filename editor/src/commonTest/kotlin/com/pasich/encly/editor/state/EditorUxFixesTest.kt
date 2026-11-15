package com.pasich.encly.editor.state

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.LinkDataBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Where the cursor goes after the toolbar, a tap below the note, a link and a move. */
class EditorUxFixesTest {
    private fun text(value: String = "") = Block.TextBlock(MutableStateFlow(value))

    private fun list(vararg values: String) =
        Block.ListBlock(MutableStateFlow(values.map { ItemListBlock(it) }), BlockType.LIST_BULLET)

    private fun editorOf(vararg blocks: Block) = BlockEditorState { 0L }.also { it.load(blocks.toList()) }

    private fun BlockEditorState.requests(): MutableList<FocusRequest> {
        val seen = mutableListOf<FocusRequest>()
        selection.immediateFocus = { seen += it }
        return seen
    }

    @Test
    fun theKeyboardReturnsToTheItemAndCursorOfAList() {
        val bullets = list("a", "bcd", "e")
        val editor = editorOf(bullets)
        val seen = editor.requests()
        editor.selection.onFocused(bullets.id)
        editor.selection.onItemCaret(bullets.id, bullets.items.value[1].id, 3)

        editor.focusWorkingBlock()

        assertEquals(bullets.items.value[1].id, seen.last().itemId)
        assertEquals(3, seen.last().caretOffset)
    }

    @Test
    fun theKeyboardFallsBackToTheListsEndWhenTheRememberedItemIsGone() {
        val bullets = list("a", "b")
        val editor = editorOf(bullets)
        val seen = editor.requests()
        editor.selection.onFocused(bullets.id)
        editor.selection.onItemCaret(bullets.id, "gone", 1)

        editor.focusWorkingBlock()

        assertNull(seen.last().itemId)
        assertEquals(Int.MAX_VALUE, seen.last().caretOffset)
    }

    @Test
    fun aTapBelowTheNoteWritesAtTheEndOfTheLastParagraph() {
        val last = text("hello")
        val editor = editorOf(last)
        val seen = editor.requests()
        editor.selection.onCaret(last.id, 2)

        editor.addBlockToEnd()

        assertEquals(Int.MAX_VALUE, seen.last().caretOffset)
    }

    @Test
    fun movingABlockWithoutFocusDoesNotBringTheKeyboardBack() {
        val first = text("a")
        val second = text("b")
        val editor = editorOf(first, second)
        val seen = editor.requests()

        editor.moveBlock(1, 0)

        assertTrue(seen.isEmpty())
        assertEquals(second.id, editor.selection.interactedBlockId.value)
    }

    @Test
    fun movingAFocusedListKeepsItsItemAndCursor() {
        val bullets = list("a", "bcd")
        val editor = editorOf(text("x"), bullets)
        val seen = editor.requests()
        editor.selection.onFocused(bullets.id)
        editor.selection.onItemCaret(bullets.id, bullets.items.value[1].id, 2)

        editor.moveBlock(1, 0)

        assertEquals(bullets.items.value[1].id, seen.last().itemId)
        assertEquals(2, seen.last().caretOffset)
    }

    @Test
    fun committingANewLinkFocusesAParagraphAfterIt() {
        val link = Block.LinkBlock()
        val editor = editorOf(link)
        val seen = editor.requests()

        assertTrue(editor.commitLink(link.id, LinkDataBlock(title = "x", url = "https://x.org")))

        assertEquals("https://x.org", link.block.value.url)
        assertEquals(2, editor.blocks.size)
        assertTrue(editor.blocks[1] is Block.TextBlock)
        assertEquals(editor.blocks[1].id, seen.last().blockId)
        editor.undo()
        assertEquals(1, editor.blocks.size)
        assertEquals("", link.block.value.url)
    }

    @Test
    fun committingALinkReusesTheParagraphThatFollows() {
        val link = Block.LinkBlock()
        val after = text("next")
        val editor = editorOf(link, after)

        assertTrue(editor.commitLink(link.id, LinkDataBlock(title = "x", url = "https://x.org")))

        assertEquals(listOf(link.id, after.id), editor.blocks.map { it.id })
        assertFalse(editor.commitLink(after.id, LinkDataBlock(url = "https://y.org")))
    }

    @Test
    fun backspaceInAnEmptyFirstParagraphFocusesTheNextBlockAtItsStart() {
        val first = text("")
        val second = text("next")
        val editor = editorOf(first, second)
        val seen = editor.requests()

        editor.removeBlock(first, BlockRemoveAction.REMOVE_BACKSPACE)

        assertEquals(second.id, seen.last().blockId)
        assertEquals(0, seen.last().caretOffset)
    }
}
