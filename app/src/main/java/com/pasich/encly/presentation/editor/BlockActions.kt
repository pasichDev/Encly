package com.pasich.encly.presentation.editor

import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.domain.model.LinkDataBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.presentation.editor.state.BlockRemoveAction
import com.pasich.encly.presentation.editor.state.FocusRequest
import com.pasich.encly.presentation.editor.state.LineBreak
import com.pasich.encly.presentation.editor.state.Shortcut

/**
 * What a block composable can do. Content edits go through [onTextChanged],
 * [onListItemsChanged] and [onLinkChanged], never by writing the block's state directly,
 * so every edit is saved and can be undone.
 */
@Suppress("TooManyFunctions") // Everything a block's fields can ask of the editor, in one place.
interface BlockActions {
    fun onAddParagraph()
    fun onRemoveBlock(blockRemoveAction: BlockRemoveAction)
    fun onReplaceBlock(type: Block)
    fun onTextChanged(newText: String)

    /** [mergeable] is true for typing, false for discrete edits (check, add/remove item). */
    fun onListItemsChanged(newItems: List<ItemListBlock>, mergeable: Boolean)
    fun onLinkChanged(newLink: LinkDataBlock)

    /**
     * A line break typed (Enter) or pasted into this block's field.
     * @return the text the field keeps; null to keep the break as text
     */
    fun onLineBreak(lineBreak: LineBreak): String?

    /** Backspace with the cursor at the start of this block's field; false when it does nothing. */
    fun onBackspaceAtStart(): Boolean

    /** A mark typed at the start of this paragraph, which now reads [typed]; true when applied. */
    fun onShortcut(typed: String, shortcut: Shortcut): Boolean

    /** A lone web address pasted into this empty paragraph; true when it became [link]. */
    fun onLinkPasted(pasted: String, link: LinkDataBlock): Boolean

    /** [onLineBreak] in item [itemId] of this list. */
    fun onListLineBreak(itemId: String, lineBreak: LineBreak): String?

    /** [onBackspaceAtStart] in item [itemId] of this list. */
    fun onListBackspaceAtStart(itemId: String): Boolean

    /** The cursor in this block's field moved to [offset]. */
    fun onCaretMoved(offset: Int)

    /**
     * Registers how to put the cursor of this block's field at an offset ([Int.MAX_VALUE]: the end).
     * @return a function that removes the registration
     */
    fun registerCaret(callback: (Int) -> Unit): () -> Unit

    /**
     * Registers how to focus a block with several fields (a list): the item a request names,
     * else its last field with the cursor at the end when asked for the end, else its first.
     * @return a function that removes the registration
     */
    fun registerFieldsFocusTarget(requestFocus: (FocusRequest) -> Unit): () -> Unit

    /** Focuses the next block; false when this is the last one. */
    fun navigateToNext(): Boolean

    /** Focuses the focusable block above; false when there is none. */
    fun navigateToPrevious(): Boolean
}
