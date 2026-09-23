package com.pasich.encly.presentation.editor

import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.domain.model.LinkDataBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.presentation.editor.state.BlockRemoveAction

/**
 * What a block composable can do. Content edits go through [onTextChanged],
 * [onListItemsChanged] and [onLinkChanged], never by writing the block's state directly,
 * so every edit is saved and can be undone.
 */
interface BlockActions {
    fun onAddParagraph()
    fun onRemoveBlock(blockRemoveAction: BlockRemoveAction)
    fun onReplaceBlock(type: Block)
    fun onTextChanged(newText: String)

    /** [mergeable] is true for typing, false for discrete edits (check, add/remove item). */
    fun onListItemsChanged(newItems: List<ItemListBlock>, mergeable: Boolean)
    fun onLinkChanged(newLink: LinkDataBlock)

    /**
     * Registers how to put the cursor at the end of this block's text.
     * @return a function that removes the registration
     */
    fun registerCursorToEnd(callback: () -> Unit): () -> Unit

    /** The user works on this block without its own field taking focus (a list item did). */
    fun onInteraction()

    /** Focuses the next block; false when this is the last one. */
    fun navigateToNext(): Boolean

    /** Focuses the focusable block above; false when there is none. */
    fun navigateToPrevious(): Boolean
}
