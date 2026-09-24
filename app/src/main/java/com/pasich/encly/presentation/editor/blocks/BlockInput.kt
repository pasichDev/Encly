package com.pasich.encly.presentation.editor.blocks

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.placeCursorAtEnd
import com.pasich.encly.presentation.editor.BlockActions
import com.pasich.encly.presentation.editor.state.LineBreak
import com.pasich.encly.presentation.editor.state.isLoneWebAddress
import com.pasich.encly.presentation.editor.state.normalizeLineBreaks
import com.pasich.encly.presentation.editor.state.textDiff
import com.pasich.encly.presentation.editor.state.typedShortcut

/** Which field of a block a [BlockInput] watches: a paragraph, another text block, or a list item. */
internal sealed interface BlockField {
    /** A paragraph: its marks ("# ", "- ", ...) and a pasted lone web address convert it. */
    data object Paragraph : BlockField

    /** A heading or quote. */
    data object Styled : BlockField

    /** Item [itemId] of a list. */
    data class ListItem(val itemId: String) : BlockField
}

/**
 * The writing rules of a block's field, applied to every edit the user makes: typing, the
 * keyboard's Enter, a paste, autocorrect. Enter arrives as a line break from every keyboard
 * (some send a key press, some commit "\n"), so this is the one place it is handled: a line
 * break typed or pasted splits the block ([BlockActions.onLineBreak]), and the field keeps only
 * the text before it. A paragraph also turns into a heading, list, quote or separator when its
 * mark is typed at its start, and into a link card when a web address alone is pasted into it.
 *
 * The editor applies each rule at once, in one undo step. When it declines (simple editing),
 * the line break stays in the text.
 */
internal class BlockInput(private val field: BlockField, private val actions: () -> BlockActions) :
    InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val old = originalText.toString()
        val new = asCharSequence().toString()
        if (old == new) return
        val cursor = if (selection.collapsed) selection.start else -1
        val diff = textDiff(old, new, cursor)
        val lineBreak = LineBreak.of(old, diff)
        when {
            lineBreak != null -> {
                val kept = when (field) {
                    is BlockField.ListItem -> actions().onListLineBreak(field.itemId, lineBreak)
                    else -> actions().onLineBreak(lineBreak)
                }
                if (kept != null) {
                    replace(0, length, kept)
                    placeCursorAtEnd()
                } else if (diff.inserted.any { it == '\r' }) {
                    // Kept as text: one kind of line break only.
                    replace(diff.start, diff.end, normalizeLineBreaks(diff.inserted))
                }
            }

            field != BlockField.Paragraph || diff.removed.isNotEmpty() -> Unit

            else -> onParagraphInsert(old, new, cursor, diff.inserted)
        }
    }

    private fun onParagraphInsert(old: String, new: String, cursor: Int, inserted: String) {
        val shortcut = typedShortcut(new, cursor)
        when {
            shortcut != null -> actions().onShortcut(new, shortcut)

            // A paste (not typing, which inserts one character at a time) of an address alone.
            old.isEmpty() && inserted.length > 1 && isLoneWebAddress(new) ->
                buildLinkData(new)?.let { actions().onLinkPasted(new, it) }
        }
    }
}
