package com.pasich.encly.editor.ui

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.LinkDataBlock
import com.pasich.encly.editor.state.BlockRemoveAction
import com.pasich.encly.editor.state.LineBreak
import com.pasich.encly.editor.state.Shortcut
import com.pasich.encly.editor.state.SlashCommand
import com.pasich.encly.editor.state.SlashPicker

/**
 * What the editor's blocks ask of whoever owns the note: the host's view model, which decides
 * whether an edit is allowed at all (a locked or read-only note changes nothing) and applies it to
 * a `BlockEditorState`. Every call names the block it is about, never a position, so one that
 * arrives after the list changed cannot hit another block.
 */
@Suppress("TooManyFunctions") // Everything the blocks can ask of the editor, in one place.
interface EditorController {
    /** The note's blocks, live. */
    val blocks: List<Block>

    /** The slash picker the blocks show. */
    val slashPicker: SlashPicker

    /** Position of [block]; -1 when it is not in the note (any more). */
    fun indexOfBlock(block: Block): Int

    /** [block] got focus. */
    fun onBlockFocused(block: Block)

    /** [block] lost focus. */
    fun onBlockFocusLost(block: Block)

    /** The user works on [block] without its field taking focus (a touch, its sheet). */
    fun onBlockInteraction(block: Block)

    /** Adds a block of [blockType] after position [afterIndex] and focuses it. */
    fun addBlockAfter(afterIndex: Int, blockType: BlockType)

    /** Focuses the trailing paragraph, adding one first unless the note already ends in one. */
    fun addBlockToEnd()

    /** Removes [block] if [action] allows it. */
    fun removeBlock(block: Block, action: BlockRemoveAction)

    /** Replaces the block at [index] with [newBlock]; false when there is none there. */
    fun replaceBlock(index: Int, newBlock: Block): Boolean

    /** Moves the block the user works on one place [up] (or down). */
    fun moveBlock(up: Boolean)

    /** Moves [block] one place [up] (or down). */
    fun moveBlockOf(block: Block, up: Boolean)

    /** Which end of the note the block the user works on is at: see [com.pasich.encly.editor.state.BLOCK_AT_TOP]. */
    fun interactedBlockPosition(): Int

    /** Duplicates [block] right after itself. */
    fun duplicateBlock(block: Block)

    /** Locks or unlocks the checklist [block]. */
    fun toggleChecklistLock(block: Block)

    /** Reopens the address of the saved link [block] for editing. */
    fun editLink(block: Block.LinkBlock)

    /** A paragraph, heading or quote [block] now reads [newText]. */
    fun onBlockTextChanged(block: Block, newText: String)

    /** The items of the list [block] changed; typing is [mergeable] into one undo step. */
    fun onListItemsChanged(block: Block.ListBlock, newItems: List<ItemListBlock>, mergeable: Boolean)

    /** The link [block] now holds [newLink]. */
    fun onLinkChanged(block: Block.LinkBlock, newLink: LinkDataBlock)

    /** A line break typed or pasted into [block]'s field; the text the field keeps, null to keep the break. */
    fun onLineBreak(block: Block, lineBreak: LineBreak): String?

    /** Backspace at the start of [block]'s field; false when it does nothing. */
    fun onBackspaceAtStart(block: Block): Boolean

    /** A mark [typed] at the start of [block]'s paragraph; true when applied. */
    fun onShortcut(block: Block, typed: String, shortcut: Shortcut): Boolean

    /** A lone web address [pasted] into the empty paragraph [block]; true when it became [link]. */
    fun onLinkPasted(block: Block, pasted: String, link: LinkDataBlock): Boolean

    /** A line break in item [itemId] of [block]. */
    fun onListLineBreak(block: Block.ListBlock, itemId: String, lineBreak: LineBreak): String?

    /** Backspace at the start of item [itemId] of [block]. */
    fun onListBackspaceAtStart(block: Block.ListBlock, itemId: String): Boolean

    /** The cursor in [block]'s field moved to [offset]. */
    fun onCaretMoved(block: Block, offset: Int)

    /** The cursor in item [itemId] of [block] moved to [offset]. */
    fun onListItemCaret(block: Block, itemId: String, offset: Int)

    /** The paragraph [block] reads [text]; [started] when a "/" was just typed into the empty paragraph. */
    fun onSlashTyped(block: Block, text: String, started: Boolean)

    /** Turns the paragraph [block] into [command]'s block. */
    fun applySlash(block: Block, command: SlashCommand)
}
