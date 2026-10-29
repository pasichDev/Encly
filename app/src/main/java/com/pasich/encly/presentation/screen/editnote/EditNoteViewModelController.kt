package com.pasich.encly.presentation.screen.editnote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.LinkDataBlock
import com.pasich.encly.editor.state.BlockRemoveAction
import com.pasich.encly.editor.state.LineBreak
import com.pasich.encly.editor.state.Shortcut
import com.pasich.encly.editor.state.SlashCommand
import com.pasich.encly.editor.state.SlashPicker
import com.pasich.encly.editor.ui.EditorController
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * The editor's view of the note's view model: every call goes to the view model's own method, so
 * its rules (a locked, read-only or simple-edit note changes nothing) apply unchanged.
 */
@Suppress("TooManyFunctions") // One call each for what the editor's blocks can ask.
internal class EditNoteViewModelController(private val viewModel: EditNoteViewModel) : EditorController {
    override val blocks: List<Block> get() = viewModel.blocks

    override val slashPicker: SlashPicker get() = viewModel.slashPicker

    override fun indexOfBlock(block: Block): Int = viewModel.indexOfBlock(block)

    override fun onBlockFocused(block: Block) = viewModel.onBlockFocused(block)

    override fun onBlockFocusLost(block: Block) = viewModel.onBlockFocusLost(block)

    override fun onBlockInteraction(block: Block) = viewModel.onBlockInteraction(block)

    override fun addBlockAfter(afterIndex: Int, blockType: BlockType) = viewModel.addBlockAfter(afterIndex, blockType)

    override fun addBlockToEnd() = viewModel.addBlockToEnd()

    override fun removeBlock(block: Block, action: BlockRemoveAction) = viewModel.removeBlock(block, action)

    override fun replaceBlock(index: Int, newBlock: Block): Boolean = viewModel.replaceBlock(index, newBlock)

    override fun moveBlock(up: Boolean) = viewModel.moveBlock(up)

    override fun moveBlockOf(block: Block, up: Boolean) = viewModel.moveBlockOf(block, up)

    override fun interactedBlockPosition(): Int = viewModel.getMoveBlockState()

    override fun duplicateBlock(block: Block) = viewModel.duplicateBlock(block)

    override fun toggleChecklistLock(block: Block) = viewModel.toggleChecklistLock(block)

    override fun editLink(block: Block.LinkBlock) = viewModel.editLink(block)

    override fun onBlockTextChanged(block: Block, newText: String) = viewModel.onBlockTextChanged(block, newText)

    override fun onListItemsChanged(block: Block.ListBlock, newItems: List<ItemListBlock>, mergeable: Boolean) =
        viewModel.onListItemsChanged(block, newItems, mergeable)

    override fun onLinkChanged(block: Block.LinkBlock, newLink: LinkDataBlock) = viewModel.onLinkChanged(block, newLink)

    override fun onLineBreak(block: Block, lineBreak: LineBreak): String? = viewModel.onLineBreak(block, lineBreak)

    override fun onBackspaceAtStart(block: Block): Boolean = viewModel.onBackspaceAtStart(block)

    override fun onShortcut(block: Block, typed: String, shortcut: Shortcut): Boolean =
        viewModel.onShortcut(block, typed, shortcut)

    override fun onLinkPasted(block: Block, pasted: String, link: LinkDataBlock): Boolean =
        viewModel.onLinkPasted(block, pasted, link)

    override fun onListLineBreak(block: Block.ListBlock, itemId: String, lineBreak: LineBreak): String? =
        viewModel.onListLineBreak(block, itemId, lineBreak)

    override fun onListBackspaceAtStart(block: Block.ListBlock, itemId: String): Boolean =
        viewModel.onListBackspaceAtStart(block, itemId)

    override fun onCaretMoved(block: Block, offset: Int) = viewModel.onCaretMoved(block, offset)

    override fun onListItemCaret(block: Block, itemId: String, offset: Int) =
        viewModel.onListItemCaret(block, itemId, offset)

    override fun onSlashTyped(block: Block, text: String, started: Boolean) =
        viewModel.onSlashTyped(block, text, started)

    override fun applySlash(block: Block, command: SlashCommand) = viewModel.applySlash(block, command)
}

/** The editor's view of the screen's note view model. */
@Composable
internal fun rememberEditorController(): EditorController {
    val viewModel: EditNoteViewModel = metroViewModel()
    return remember(viewModel) { EditNoteViewModelController(viewModel) }
}
