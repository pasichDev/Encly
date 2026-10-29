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
public interface EditorController {
    /** The note's blocks, live. */
    public val blocks: List<Block>

    /** The slash picker the blocks show. */
    public val slashPicker: SlashPicker

    /** Position of [block]; -1 when it is not in the note (any more). */
    public fun indexOfBlock(block: Block): Int

    /** [block] got focus. */
    public fun onBlockFocused(block: Block)

    /** [block] lost focus. */
    public fun onBlockFocusLost(block: Block)

    /** The user works on [block] without its field taking focus (a touch, its sheet). */
    public fun onBlockInteraction(block: Block)

    /** Adds a block of [blockType] after position [afterIndex] and focuses it. */
    public fun addBlockAfter(afterIndex: Int, blockType: BlockType)

    /** Focuses the trailing paragraph, adding one first unless the note already ends in one. */
    public fun addBlockToEnd()

    /** Removes [block] if [action] allows it. */
    public fun removeBlock(block: Block, action: BlockRemoveAction)

    /** Replaces the block at [index] with [newBlock]; false when there is none there. */
    public fun replaceBlock(index: Int, newBlock: Block): Boolean

    /** Moves the block the user works on one place [up] (or down). */
    public fun moveBlock(up: Boolean)

    /** Moves [block] one place [up] (or down). */
    public fun moveBlockOf(block: Block, up: Boolean)

    /** Which end of the note the block the user works on is at: see [com.pasich.encly.editor.state.BLOCK_AT_TOP]. */
    public fun interactedBlockPosition(): Int

    /** Duplicates [block] right after itself. */
    public fun duplicateBlock(block: Block)

    /** Locks or unlocks the checklist [block]. */
    public fun toggleChecklistLock(block: Block)

    /** Reopens the address of the saved link [block] for editing. */
    public fun editLink(block: Block.LinkBlock)

    /** A paragraph, heading or quote [block] now reads [newText]. */
    public fun onBlockTextChanged(block: Block, newText: String)

    /** The items of the list [block] changed; typing is [mergeable] into one undo step. */
    public fun onListItemsChanged(block: Block.ListBlock, newItems: List<ItemListBlock>, mergeable: Boolean)

    /** The link [block] now holds [newLink]. */
    public fun onLinkChanged(block: Block.LinkBlock, newLink: LinkDataBlock)

    /** A line break typed or pasted into [block]'s field; the text the field keeps, null to keep the break. */
    public fun onLineBreak(block: Block, lineBreak: LineBreak): String?

    /** Backspace at the start of [block]'s field; false when it does nothing. */
    public fun onBackspaceAtStart(block: Block): Boolean

    /** A mark [typed] at the start of [block]'s paragraph; true when applied. */
    public fun onShortcut(block: Block, typed: String, shortcut: Shortcut): Boolean

    /** A lone web address [pasted] into the empty paragraph [block]; true when it became [link]. */
    public fun onLinkPasted(block: Block, pasted: String, link: LinkDataBlock): Boolean

    /** A line break in item [itemId] of [block]. */
    public fun onListLineBreak(block: Block.ListBlock, itemId: String, lineBreak: LineBreak): String?

    /** Backspace at the start of item [itemId] of [block]. */
    public fun onListBackspaceAtStart(block: Block.ListBlock, itemId: String): Boolean

    /** The cursor in [block]'s field moved to [offset]. */
    public fun onCaretMoved(block: Block, offset: Int)

    /** The cursor in item [itemId] of [block] moved to [offset]. */
    public fun onListItemCaret(block: Block, itemId: String, offset: Int)

    /** The paragraph [block] reads [text]; [started] when a "/" was just typed into the empty paragraph. */
    public fun onSlashTyped(block: Block, text: String, started: Boolean)

    /** Turns the paragraph [block] into [command]'s block. */
    public fun applySlash(block: Block, command: SlashCommand)
}
