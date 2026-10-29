package com.pasich.encly.editor.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pasich.encly.editor.EditorProvider
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockFactory
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.LinkDataBlock
import com.pasich.encly.editor.model.TextualBlock
import com.pasich.encly.editor.state.BlockEditorState
import com.pasich.encly.editor.state.BlockRemoveAction
import com.pasich.encly.editor.state.LineBreak
import com.pasich.encly.editor.state.Shortcut
import com.pasich.encly.editor.state.SlashCommand
import com.pasich.encly.editor.state.SlashPicker
import com.pasich.encly.editor.state.addBlockAfter
import com.pasich.encly.editor.state.addBlockToEnd
import com.pasich.encly.editor.state.applyShortcut
import com.pasich.encly.editor.state.applySlash
import com.pasich.encly.editor.state.backspaceAtStart
import com.pasich.encly.editor.state.breakLine
import com.pasich.encly.editor.state.duplicateBlockOf
import com.pasich.encly.editor.state.interactedBlockPosition
import com.pasich.encly.editor.state.listBackspaceAtStart
import com.pasich.encly.editor.state.listLineBreak
import com.pasich.encly.editor.state.listTypingStep
import com.pasich.encly.editor.state.moveBlockOf
import com.pasich.encly.editor.state.moveInteracted
import com.pasich.encly.editor.state.pasteLink
import com.pasich.encly.editor.state.toggleChecklistLock
import com.pasich.encly.editor.ui.sheets.BlockSheetKind

/**
 * An [EditorController] over a plain [BlockEditorState], doing what a note's view model does
 * without the gating of a locked or read-only note: the editor's blocks can be driven on their own,
 * with nothing of the app around them.
 */
internal class StateController(val state: BlockEditorState) : EditorController {
    var editingLinks by mutableStateOf(emptySet<String>())
        private set

    override val blocks: List<Block> get() = state.blocks

    override val slashPicker: SlashPicker get() = state.slash

    override fun indexOfBlock(block: Block): Int = state.blocks.indexOfFirst { it.id == block.id }

    override fun onBlockFocused(block: Block) = state.selection.onFocused(block.id)

    override fun onBlockFocusLost(block: Block) {
        state.selection.onFocusLost(block.id)
        if (state.slash.menu.value?.blockId == block.id) state.slash.dismiss()
    }

    override fun onBlockInteraction(block: Block) {
        state.selection.onInteraction(block.id)
        state.slash.dismiss()
    }

    override fun addBlockAfter(afterIndex: Int, blockType: BlockType) =
        state.addBlockAfter(afterIndex, BlockFactory.createBlock(blockType))

    override fun addBlockToEnd() = state.addBlockToEnd()

    override fun removeBlock(block: Block, action: BlockRemoveAction) = state.removeBlock(block, action)

    override fun replaceBlock(index: Int, newBlock: Block): Boolean = state.replaceBlock(index, newBlock)

    override fun moveBlock(up: Boolean) = state.moveInteracted(up)

    override fun moveBlockOf(block: Block, up: Boolean) = state.moveBlockOf(block.id, up)

    override fun interactedBlockPosition(): Int = state.interactedBlockPosition()

    override fun duplicateBlock(block: Block) {
        state.duplicateBlockOf(block.id)
    }

    override fun toggleChecklistLock(block: Block) {
        state.toggleChecklistLock(block.id)
    }

    override fun editLink(block: Block.LinkBlock) {
        editingLinks = editingLinks + block.id
    }

    override fun onBlockTextChanged(block: Block, newText: String) {
        if (block is TextualBlock) state.changeValue(block.id, block.text, newText, mergeable = true)
    }

    override fun onListItemsChanged(block: Block.ListBlock, newItems: List<ItemListBlock>, mergeable: Boolean) {
        val step = if (mergeable) listTypingStep(block.items.value, newItems) else null
        state.changeValue(block.id, block.items, newItems, mergeable, step)
    }

    override fun onLinkChanged(block: Block.LinkBlock, newLink: LinkDataBlock) {
        editingLinks = editingLinks - block.id
        state.changeValue(block.id, block.block, newLink, mergeable = false)
    }

    override fun onLineBreak(block: Block, lineBreak: LineBreak): String? {
        val picked = state.slash.menu.value
            ?.takeIf { it.blockId == block.id && lineBreak.isEnter && it.isShown }
            ?.highlightedCommand
        return if (picked != null && state.applySlash(block.id, picked)) "" else state.breakLine(block.id, lineBreak)
    }

    override fun onBackspaceAtStart(block: Block): Boolean = state.backspaceAtStart(block.id)

    override fun onShortcut(block: Block, typed: String, shortcut: Shortcut): Boolean =
        state.applyShortcut(block.id, typed, shortcut)

    override fun onLinkPasted(block: Block, pasted: String, link: LinkDataBlock): Boolean =
        state.pasteLink(block.id, pasted, link)

    override fun onListLineBreak(block: Block.ListBlock, itemId: String, lineBreak: LineBreak): String? =
        state.listLineBreak(block.id, itemId, lineBreak)

    override fun onListBackspaceAtStart(block: Block.ListBlock, itemId: String): Boolean =
        state.listBackspaceAtStart(block.id, itemId)

    override fun onCaretMoved(block: Block, offset: Int) = state.selection.onCaret(block.id, offset)

    override fun onListItemCaret(block: Block, itemId: String, offset: Int) =
        state.selection.onItemCaret(block.id, itemId, offset)

    override fun onSlashTyped(block: Block, text: String, started: Boolean) {
        if (started && block is Block.TextBlock) state.slash.open(block.id)
        state.slash.onText(block.id, text)
    }

    override fun applySlash(block: Block, command: SlashCommand) {
        state.applySlash(block.id, command)
    }
}

/** The editor's blocks in a list, as a note screen shows them, with focus carried out as the screen does. */
@Composable
internal fun EditorHarness(
    controller: StateController,
    onOpenSheet: (BlockSheetKind, Block) -> Unit,
    isLocked: Boolean = false,
    afterBlocks: (@Composable () -> Unit)? = null,
) {
    val state = controller.state
    MaterialTheme {
        EditorProvider {
            val registry = rememberBlockFocusRegistry(state.blocks)
            val findState = state.find.state.collectAsState()
            val highlights = remember(findState) { FindHighlights(findState) }
            val host = remember(controller, registry, highlights) {
                EditorBlocksHost(controller, registry, highlights, onOpenSheet)
            }
            LaunchedEffect(registry) {
                state.selection.immediateFocus = { registry.focus(it) }
                state.selection.requests.collect { registry.focusWhenComposed(it) }
            }
            LazyColumn {
                editorBlocks(host, state.blocks, isLocked, controller.editingLinks, afterBlocks)
            }
        }
    }
}
