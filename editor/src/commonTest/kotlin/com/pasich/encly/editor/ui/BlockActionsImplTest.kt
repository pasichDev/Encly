package com.pasich.encly.editor.ui

import androidx.compose.runtime.mutableStateOf
import com.pasich.encly.editor.focus.BlockFocusRegistry
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.LinkDataBlock
import com.pasich.encly.editor.state.BlockEditorState
import com.pasich.encly.editor.state.BlockRemoveAction
import com.pasich.encly.editor.state.CaretMove
import com.pasich.encly.editor.state.FindState
import com.pasich.encly.editor.state.LineBreak
import com.pasich.encly.editor.state.Shortcut
import com.pasich.encly.editor.state.SlashCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The actions a block's fields call: each reaches the controller for that block, or does nothing when it can't. */
class BlockActionsImplTest {
    private val paragraph = Block.TextBlock()
    private val next = Block.TextBlock()
    private val list = Block.ListBlock(blockType = BlockType.LIST_BULLET)
    private val link = Block.LinkBlock()
    private val state = BlockEditorState { 0L }.also { it.load(listOf(paragraph, next, list, link)) }
    private val controller = StateController(state)
    private val registry = BlockFocusRegistry { state.blocks }
    private val highlights = FindHighlights(mutableStateOf(FindState()))

    private fun actions(block: Block) = BlockActionsImpl(block, controller, registry, highlights)

    @Test
    fun anAddedParagraphGoesAfterTheBlock() {
        actions(paragraph).onAddParagraph()
        assertEquals(5, state.blocks.size)
        assertTrue(state.blocks[1] is Block.TextBlock)
    }

    @Test
    fun aBlockThatLeftTheNoteAddsAndReplacesNothing() {
        val gone = Block.TextBlock()
        actions(gone).onAddParagraph()
        actions(gone).onReplaceBlock(Block.SeparatorBlock())
        assertEquals(4, state.blocks.size)
    }

    @Test
    fun replacingAndRemovingActOnTheBlock() {
        actions(next).onReplaceBlock(Block.SeparatorBlock())
        assertTrue(state.blocks[1] is Block.SeparatorBlock)
        actions(state.blocks[1]).onRemoveBlock(BlockRemoveAction.REMOVE)
        assertEquals(3, state.blocks.size)
    }

    @Test
    fun editsOfTheWrongKindOfBlockAreIgnored() {
        actions(paragraph).onListItemsChanged(listOf(ItemListBlock("x")), mergeable = false)
        actions(paragraph).onLinkChanged(LinkDataBlock(url = "https://x.org"))
        assertNull(actions(paragraph).onListLineBreak("id", LineBreak("", listOf("", ""), "")))
        assertFalse(actions(paragraph).onListBackspaceAtStart("id"))
        assertEquals("", (state.blocks[0] as Block.TextBlock).text.value)
    }

    @Test
    fun editsOfTheRightKindReachTheModel() {
        actions(paragraph).onTextChanged("typed")
        actions(list).onListItemsChanged(listOf(ItemListBlock("item")), mergeable = false)
        actions(link).onLinkChanged(LinkDataBlock(url = "https://x.org"))
        actions(paragraph).onCaretMoved(2)
        actions(list).onListItemCaret(list.items.value.first().id, 1)

        assertEquals("typed", paragraph.text.value)
        assertEquals("item", list.items.value.single().value)
        assertEquals("https://x.org", link.block.value.url)
        assertEquals(2, state.selection.caretOf(paragraph.id))
    }

    @Test
    fun theSlashPickerOfAParagraphIsTheEditorsOne() {
        val slash = actions(paragraph).slash
        assertEquals(paragraph.id, slash.blockId)
        actions(paragraph).onSlashTyped("/", started = true)
        assertEquals(paragraph.id, slash.menu.value?.blockId)
        slash.setLabels(SlashCommand.entries.associateWith { it.name })
        slash.commit()
        slash.moveHighlight(1)
        slash.select(SlashCommand.QUOTE)
        slash.dismiss()
        assertTrue(state.blocks[0] is Block.QuoteBlock)
    }

    @Test
    fun registrationsAreForTheBlockAndCanBeUndone() {
        val a = actions(paragraph)
        var offset = -1
        var x = -1f
        val undoCaret = a.registerCaret { offset = it }
        val undoCaretX = a.registerCaretAtX { value, _ -> x = value }
        val undoFields = a.registerFieldsFocusTarget { }
        registry.registerFocusTarget(paragraph.id) { }

        registry.focus(com.pasich.encly.editor.state.FocusRequest(paragraph.id, caret = 4))
        registry.focus(com.pasich.encly.editor.state.FocusRequest(paragraph.id, caretX = 7f))
        undoCaret()
        undoCaretX()
        undoFields()

        assertEquals(4, offset)
        assertEquals(7f, x, 0f)
    }

    @Test
    fun cursorKeysMoveToTheNextFieldOnlyWhereThereIsOne() {
        val a = actions(paragraph)
        assertFalse(a.navigate(CaretMove.Down(null)))
        registry.registerFocusTarget(next.id) { }
        assertTrue(a.navigate(CaretMove.Down(null)))
        assertFalse(actions(paragraph).navigate(CaretMove.Up(null)))
        registry.registerFocusTarget(paragraph.id) { }
        assertTrue(actions(next).navigate(CaretMove.Left))
    }

    @Test
    fun theFacadeForwardsBreaksShortcutsAndPastes() {
        val a = actions(paragraph)
        assertTrue(a.onLineBreak(LineBreak("", listOf("", ""), "")) != null)
        assertFalse(a.onBackspaceAtStart())
        assertTrue(a.onShortcut("x", Shortcut(BlockType.QUOTE, "> ")))
        assertFalse(a.onLinkPasted("https://x.org", LinkDataBlock(url = "https://x.org")))
    }
}
