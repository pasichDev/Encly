package com.pasich.encly.editor.state

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The branches of list indentation, moving a focused block, focusing by request and pasting blank lines. */
class CoverageGapsTest {
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
    fun aTabAndTwoSpacesAreOneLevelEach() {
        assertEquals(1, indentLevel("\t- a"))
        assertEquals(2, indentLevel("\t\t- a"))
        assertEquals(1, indentLevel("  - a"))
        assertEquals(0, indentLevel(" - a"))
        assertEquals(0, indentLevel("a"))
    }

    @Test
    fun aMovedListKeepsTheItemAndCaretItWasIn() {
        val bullets = list("a", "bcd")
        val editor = editorOf(bullets, text("x"))
        val seen = editor.requests()
        editor.selection.onFocused(bullets.id)
        editor.selection.onItemCaret(bullets.id, bullets.items.value[1].id, 2)

        editor.moveBlock(0, 1)

        assertEquals(bullets.items.value[1].id, seen.last().itemId)
        assertEquals(2, seen.last().caretOffset)
    }

    @Test
    fun aMovedListWhoseRememberedItemIsGoneGoesToItsEnd() {
        val bullets = list("a", "b")
        val editor = editorOf(bullets, text("x"))
        val seen = editor.requests()
        editor.selection.onFocused(bullets.id)
        editor.selection.onItemCaret(bullets.id, "gone", 1)

        editor.moveBlock(0, 1)

        assertNull(seen.last().itemId)
        assertEquals(Int.MAX_VALUE, seen.last().caretOffset)
    }

    @Test
    fun aMovedListThatNeverHadTheCursorGoesToItsEnd() {
        val bullets = list("a", "b")
        val editor = editorOf(bullets, text("x"))
        val seen = editor.requests()
        editor.selection.onFocused(bullets.id)

        editor.moveBlock(0, 1)

        assertNull(seen.last().itemId)
        assertEquals(Int.MAX_VALUE, seen.last().caretOffset)
    }

    @Test
    fun aMovedParagraphKeepsItsCaret() {
        val first = text("hello")
        val editor = editorOf(first, text("x"))
        val seen = editor.requests()
        editor.selection.onFocused(first.id)
        editor.selection.onCaret(first.id, 3)

        editor.moveBlock(0, 1)

        assertEquals(3, seen.last().caretOffset)
    }

    @Test
    fun aMovedBlockThatIsNotAListKeepsAnItemRememberedForIt() {
        val first = text("hello")
        val editor = editorOf(first, text("x"))
        val seen = editor.requests()
        editor.selection.onFocused(first.id)
        editor.selection.onItemCaret(first.id, "item", 4)

        editor.moveBlock(0, 1)

        assertEquals("item", seen.last().itemId)
        assertEquals(4, seen.last().caretOffset)
    }

    @Test
    fun aRequestForABlockThatIsGoneFocusesNothing() {
        val editor = editorOf(text("a"))
        val seen = editor.requests()

        editor.requestFocus(FocusRequest("gone"))

        assertEquals(0, seen.size)
    }

    @Test
    fun aRequestForABlockFocusesIt() {
        val only = text("a")
        val editor = editorOf(only)
        val seen = editor.requests()

        editor.requestFocus(FocusRequest(only.id, caret = 1))

        assertEquals(1, seen.last().caretOffset)
    }

    @Test
    fun blankLinesPastedAfterTextLeaveTheItemAsItWas() {
        val bullets = list("ab")
        val editor = editorOf(bullets)
        val seen = editor.requests()
        editor.rememberCopied("x", listOf(text("")))

        val kept = editor.pasteCopied(bullets.id, bullets.items.value[0].id, "ab", "x", "")

        assertEquals("ab", kept)
        assertEquals("ab", bullets.items.value[0].value)
        assertEquals(2, seen.last().caretOffset)
    }

    @Test
    fun aPastedLineJoinsTheTextAfterOrReplacesAnEmptyItemsMark() {
        val bullets = list("ab", "")
        val editor = editorOf(bullets)
        editor.rememberCopied("y", listOf(text("- y")))

        val joined = editor.pasteCopied(bullets.id, bullets.items.value[0].id, "ab", "y", "")
        val replaced = editor.pasteCopied(bullets.id, bullets.items.value[1].id, "", "y", "")

        assertEquals("ab- y", joined)
        assertEquals("y", replaced)
    }
}
