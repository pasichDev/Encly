package com.pasich.encly.editor.ui

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.LinkDataBlock
import com.pasich.encly.editor.state.BlockRemoveAction
import com.pasich.encly.editor.state.CaretMove
import com.pasich.encly.editor.state.FocusRequest
import com.pasich.encly.editor.state.LineBreak
import com.pasich.encly.editor.state.Shortcut
import com.pasich.encly.editor.state.SlashCommand
import com.pasich.encly.editor.state.SlashMenu
import kotlinx.coroutines.flow.StateFlow

/**
 * What a block composable can do. Content edits go through [onTextChanged],
 * [onListItemsChanged] and [onLinkChanged], never by writing the block's state directly,
 * so every edit is saved and can be undone.
 */
@Suppress("TooManyFunctions") // Everything a block's fields can ask of the editor, in one place.
internal interface BlockActions {
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

    /**
     * A cursor key leaves this block's field ([move]): focuses the next or previous block that has a
     * field, the cursor placed by the key. False when there is none (or it is not on screen), and the
     * key is then left to the field. Nothing is ever added by it.
     */
    fun navigate(move: CaretMove): Boolean

    /**
     * Registers how to put the cursor of this block's field at a horizontal position (on its last
     * line when it arrives from below, else its first).
     * @return a function that removes the registration
     */
    fun registerCaretAtX(callback: (Float, Boolean) -> Unit): () -> Unit

    /** The cursor in item [itemId] of this list moved to [offset]. */
    fun onListItemCaret(itemId: String, offset: Int)

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

    /** What Find in this note tints in this block's fields. */
    val highlights: FindHighlights

    /** The slash picker of this paragraph. */
    val slash: SlashActions

    /**
     * This paragraph now reads [text]. [started] when a "/" was just typed into the empty paragraph;
     * otherwise it keeps an open slash picker in step with the text.
     */
    fun onSlashTyped(text: String, started: Boolean)
}

/** What a paragraph's slash picker can do. The editor decides whether it may open (Simple Edit, read-only). */
internal interface SlashActions {
    /** The paragraph this picker belongs to: the menu shows only when it is open on this one. */
    val blockId: String

    /** The picker as it is now; null while none is open on any paragraph. */
    val menu: StateFlow<SlashMenu?>

    /** The "/" is committed text: the picker may show. */
    fun commit()

    /** Turns this paragraph into [command]'s block. */
    fun select(command: SlashCommand)

    /** Closes the picker; the typed text stays. */
    fun dismiss()

    /** Moves the highlight [delta] places. */
    fun moveHighlight(delta: Int)

    /** The localized labels the query is matched against. */
    fun setLabels(labels: Map<SlashCommand, String>)
}
