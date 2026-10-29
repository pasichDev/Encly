package com.pasich.encly.editor.ui

import com.pasich.encly.editor.focus.BlockFocusRegistry
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.LinkDataBlock
import com.pasich.encly.editor.state.BlockRemoveAction
import com.pasich.encly.editor.state.CaretMove
import com.pasich.encly.editor.state.FocusRequest
import com.pasich.encly.editor.state.LineBreak
import com.pasich.encly.editor.state.Shortcut
import com.pasich.encly.editor.state.SlashCommand
import com.pasich.encly.editor.state.SlashMenu
import com.pasich.encly.editor.state.goesUp
import com.pasich.encly.editor.state.requestFor
import kotlinx.coroutines.flow.StateFlow

/**
 * Block actions bound to one [block]. Its position is looked up when an action runs, so an
 * action never targets whatever block happens to sit at a stale index. Focus moves go through
 * [focusRegistry], which owns the fields' FocusRequesters.
 */
@Suppress("TooManyFunctions") // Implements BlockActions, one editor call each.
internal class BlockActionsImpl(
    private val block: Block,
    private val controller: EditorController,
    private val focusRegistry: BlockFocusRegistry,
    override val highlights: FindHighlights,
) : BlockActions {
    private val index: Int get() = controller.indexOfBlock(block)

    override fun onAddParagraph() {
        val current = index
        if (current >= 0) controller.addBlockAfter(current, BlockType.TEXT)
    }

    override fun onRemoveBlock(blockRemoveAction: BlockRemoveAction) {
        controller.removeBlock(block, blockRemoveAction)
    }

    override fun onReplaceBlock(type: Block) {
        val current = index
        if (current >= 0) controller.replaceBlock(current, type)
    }

    override fun onTextChanged(newText: String) {
        controller.onBlockTextChanged(block, newText)
    }

    override fun onListItemsChanged(newItems: List<ItemListBlock>, mergeable: Boolean) {
        if (block is Block.ListBlock) controller.onListItemsChanged(block, newItems, mergeable)
    }

    override fun onLinkChanged(newLink: LinkDataBlock) {
        if (block is Block.LinkBlock) controller.onLinkChanged(block, newLink)
    }

    override fun onLineBreak(lineBreak: LineBreak): String? = controller.onLineBreak(block, lineBreak)

    override fun onBackspaceAtStart(): Boolean = controller.onBackspaceAtStart(block)

    override fun onShortcut(typed: String, shortcut: Shortcut): Boolean = controller.onShortcut(block, typed, shortcut)

    override fun onLinkPasted(pasted: String, link: LinkDataBlock): Boolean =
        controller.onLinkPasted(block, pasted, link)

    override fun onListLineBreak(itemId: String, lineBreak: LineBreak): String? =
        (block as? Block.ListBlock)?.let { controller.onListLineBreak(it, itemId, lineBreak) }

    override fun onListBackspaceAtStart(itemId: String): Boolean =
        (block as? Block.ListBlock)?.let { controller.onListBackspaceAtStart(it, itemId) } == true

    override fun onCaretMoved(offset: Int) = controller.onCaretMoved(block, offset)

    override fun onListItemCaret(itemId: String, offset: Int) = controller.onListItemCaret(block, itemId, offset)

    override fun registerCaret(callback: (Int) -> Unit): () -> Unit = focusRegistry.registerCaret(block.id, callback)

    override fun registerFieldsFocusTarget(requestFocus: (FocusRequest) -> Unit): () -> Unit =
        focusRegistry.registerFieldsFocusTarget(block.id, requestFocus)

    override val slash: SlashActions = object : SlashActions {
        override val blockId: String get() = block.id

        override val menu: StateFlow<SlashMenu?> get() = controller.slashPicker.menu

        override fun commit() = controller.slashPicker.commit()

        override fun select(command: SlashCommand) {
            controller.applySlash(block, command)
        }

        override fun dismiss() = controller.slashPicker.dismiss()

        override fun moveHighlight(delta: Int) = controller.slashPicker.moveHighlight(delta)

        override fun setLabels(labels: Map<SlashCommand, String>) = controller.slashPicker.setLabels(labels)
    }

    override fun onSlashTyped(text: String, started: Boolean) = controller.onSlashTyped(block, text, started)

    override fun navigate(move: CaretMove): Boolean {
        val target = if (move.goesUp) {
            focusRegistry.previousFocusableBlock(block.id)
        } else {
            focusRegistry.nextFocusableBlock(block.id)
        }
        // Only a field that is on screen can be focused; otherwise the key stays with the field.
        val moved = target != null && focusRegistry.focus(move.requestFor(target.id))
        if (moved) controller.onBlockFocused(target!!)
        return moved
    }

    override fun registerCaretAtX(callback: (Float, Boolean) -> Unit): () -> Unit =
        focusRegistry.registerCaretAtX(block.id, callback)
}
