package com.pasich.encly.presentation.editor

import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.domain.model.LinkDataBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.presentation.editor.focus.BlockFocusRegistry
import com.pasich.encly.presentation.editor.state.BlockRemoveAction
import com.pasich.encly.presentation.editor.state.CaretMove
import com.pasich.encly.presentation.editor.state.FocusRequest
import com.pasich.encly.presentation.editor.state.LineBreak
import com.pasich.encly.presentation.editor.state.Shortcut
import com.pasich.encly.presentation.editor.state.SlashCommand
import com.pasich.encly.presentation.editor.state.SlashMenu
import com.pasich.encly.presentation.editor.state.goesUp
import com.pasich.encly.presentation.editor.state.requestFor
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Block actions bound to one [block]. Its position is looked up when an action runs, so an
 * action never targets whatever block happens to sit at a stale index. Focus moves go through
 * [focusRegistry], which owns the fields' FocusRequesters.
 */
@Suppress("TooManyFunctions") // Implements BlockActions, one editor call each.
internal class BlockActionsImpl(
    private val block: Block,
    private val viewModel: EditNoteViewModel,
    private val focusRegistry: BlockFocusRegistry,
    override val highlights: FindHighlights,
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

    override fun onLineBreak(lineBreak: LineBreak): String? = viewModel.onLineBreak(block, lineBreak)

    override fun onBackspaceAtStart(): Boolean = viewModel.onBackspaceAtStart(block)

    override fun onShortcut(typed: String, shortcut: Shortcut): Boolean = viewModel.onShortcut(block, typed, shortcut)

    override fun onLinkPasted(pasted: String, link: LinkDataBlock): Boolean =
        viewModel.onLinkPasted(block, pasted, link)

    override fun onListLineBreak(itemId: String, lineBreak: LineBreak): String? =
        (block as? Block.ListBlock)?.let { viewModel.onListLineBreak(it, itemId, lineBreak) }

    override fun onListBackspaceAtStart(itemId: String): Boolean =
        (block as? Block.ListBlock)?.let { viewModel.onListBackspaceAtStart(it, itemId) } == true

    override fun onCaretMoved(offset: Int) = viewModel.onCaretMoved(block, offset)

    override fun onListItemCaret(itemId: String, offset: Int) = viewModel.onListItemCaret(block, itemId, offset)

    override fun registerCaret(callback: (Int) -> Unit): () -> Unit = focusRegistry.registerCaret(block.id, callback)

    override fun registerFieldsFocusTarget(requestFocus: (FocusRequest) -> Unit): () -> Unit =
        focusRegistry.registerFieldsFocusTarget(block.id, requestFocus)

    override val slash: SlashActions = object : SlashActions {
        override val blockId: String get() = block.id

        override val menu: StateFlow<SlashMenu?> get() = viewModel.slashPicker.menu

        override fun commit() = viewModel.slashPicker.commit()

        override fun select(command: SlashCommand) {
            viewModel.applySlash(block, command)
        }

        override fun dismiss() = viewModel.slashPicker.dismiss()

        override fun moveHighlight(delta: Int) = viewModel.slashPicker.moveHighlight(delta)

        override fun setLabels(labels: Map<SlashCommand, String>) = viewModel.slashPicker.setLabels(labels)
    }

    override fun onSlashTyped(text: String, started: Boolean) = viewModel.onSlashTyped(block, text, started)

    override fun navigate(move: CaretMove): Boolean {
        val target = if (move.goesUp) {
            focusRegistry.previousFocusableBlock(block.id)
        } else {
            focusRegistry.nextFocusableBlock(block.id)
        }
        // Only a field that is on screen can be focused; otherwise the key stays with the field.
        val moved = target != null && focusRegistry.focus(move.requestFor(target.id))
        if (moved) viewModel.onBlockFocused(target!!)
        return moved
    }

    override fun registerCaretAtX(callback: (Float, Boolean) -> Unit): () -> Unit =
        focusRegistry.registerCaretAtX(block.id, callback)
}
