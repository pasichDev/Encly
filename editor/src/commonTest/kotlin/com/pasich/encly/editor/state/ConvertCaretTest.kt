package com.pasich.encly.editor.state

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** A block tool keeps the cursor where it was: in the same list item, or at the same place in the text. */
class ConvertCaretTest {
    private val editor = BlockEditorState()

    private suspend fun converted(tool: BlockType): FocusRequest {
        editor.applyTool(tool)
        return editor.selection.requests.first()
    }

    @Test
    fun aListTurnedIntoAnotherKindKeepsTheCursorInTheSameItem() = runTest {
        val items = listOf(ItemListBlock("Milk"), ItemListBlock("Eggs"), ItemListBlock("bread"))
        val list = Block.ListBlock(MutableStateFlow(items), blockType = BlockType.LIST_CHECK)
        editor.load(listOf(list))
        editor.selection.onFocused(list.id)
        editor.selection.onItemCaret(list.id, items[2].id, 3)

        val request = converted(BlockType.LIST_BULLET)

        assertEquals(items[2].id, request.itemId)
        assertEquals(3, request.caret)
    }

    @Test
    fun aParagraphTurnedIntoAListPutsTheCursorInTheLineItWasOn() = runTest {
        val block = Block.TextBlock(MutableStateFlow("one\ntwo\nthree"))
        editor.load(listOf(block))
        editor.selection.onFocused(block.id)
        // "one\ntw|o": the second line, after "tw".
        editor.selection.onCaret(block.id, 6)

        val request = converted(BlockType.LIST_BULLET)
        val list = editor.blocks.single() as Block.ListBlock

        assertEquals(list.items.value[1].id, request.itemId)
        assertEquals(2, request.caret)
    }

    @Test
    fun aListTurnedBackIntoAParagraphPutsTheCursorAtTheSamePlaceInItsText() = runTest {
        val items = listOf(ItemListBlock("one"), ItemListBlock("two"), ItemListBlock("three"))
        val list = Block.ListBlock(MutableStateFlow(items), blockType = BlockType.LIST_BULLET)
        editor.load(listOf(list))
        editor.selection.onFocused(list.id)
        editor.selection.onItemCaret(list.id, items[2].id, 2)

        val request = converted(BlockType.LIST_BULLET)

        assertEquals(null, request.itemId)
        // "one\ntwo\nth|ree"
        assertEquals(10, request.caret)
    }

    @Test
    fun undoAndRedoOfAConversionKeepTheCursorInTheSameItem() = runTest {
        val items = listOf(ItemListBlock("Milk"), ItemListBlock("Eggs"), ItemListBlock("bread"))
        val list = Block.ListBlock(MutableStateFlow(items), blockType = BlockType.LIST_CHECK)
        editor.load(listOf(list))
        editor.selection.onFocused(list.id)
        editor.selection.onItemCaret(list.id, items[1].id, 2)
        converted(BlockType.LIST_BULLET)
        val bullets = editor.blocks.single()

        repeat(2) {
            editor.undo()
            assertEquals(FocusRequest(list.id, caret = 2, itemId = items[1].id), editor.selection.requests.first())
            editor.redo()
            assertEquals(FocusRequest(bullets.id, caret = 2, itemId = items[1].id), editor.selection.requests.first())
        }
    }
}
