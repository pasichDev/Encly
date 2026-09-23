package com.pasich.encly.presentation.editor

import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.domain.model.LinkDataBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.presentation.editor.focus.BlockFocusRegistry
import com.pasich.encly.presentation.editor.state.BlockRemoveAction
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel

/**
 * Block actions bound to one [block]. Its position is looked up when an action runs, so an
 * action never targets whatever block happens to sit at a stale index. Focus moves go through
 * [focusRegistry], which owns the fields' FocusRequesters.
 */
internal class BlockActionsImpl(
    private val block: Block,
    private val viewModel: EditNoteViewModel,
    private val focusRegistry: BlockFocusRegistry,
) : BlockActions {
    private val index: Int get() = viewModel.indexOfBlock(block)

    override fun onAddParagraph() {
        val current = index
        if (current >= 0) viewModel.addBlockAfter(current, BlockType.TEXT)
    }

    override fun onRemoveBlock(blockRemoveAction: BlockRemoveAction) {
        viewModel.removeBlock(block, blockRemoveAction)
    }

    override fun onReplaceBlock(type: Block) {
        val current = index
        if (current >= 0) viewModel.replaceBlock(current, type)
    }

    override fun onTextChanged(newText: String) {
        viewModel.onBlockTextChanged(block, newText)
    }

    override fun onListItemsChanged(newItems: List<ItemListBlock>, mergeable: Boolean) {
        if (block is Block.ListBlock) viewModel.onListItemsChanged(block, newItems, mergeable)
    }

    override fun onLinkChanged(newLink: LinkDataBlock) {
        if (block is Block.LinkBlock) viewModel.onLinkChanged(block, newLink)
    }

    override fun registerCursorToEnd(callback: () -> Unit): () -> Unit =
        focusRegistry.registerCursorToEnd(block.id, callback)

    override fun onInteraction() {
        viewModel.onBlockInteraction(block)
    }

    override fun navigateToNext(): Boolean =
        viewModel.moveFocusTo(focusRegistry.nextBlock(block.id), focusRegistry, cursorToEnd = false)

    override fun navigateToPrevious(): Boolean =
        viewModel.moveFocusTo(focusRegistry.previousFocusableBlock(block.id), focusRegistry, cursorToEnd = true)
}

/** Focuses [target] now (its field is on screen) and records it as the block the user works on. */
private fun EditNoteViewModel.moveFocusTo(
    target: Block?,
    focusRegistry: BlockFocusRegistry,
    cursorToEnd: Boolean,
): Boolean {
    if (target == null) return false
    onBlockFocused(target)
    focusRegistry.focus(target.id, cursorToEnd)
    return true
}
