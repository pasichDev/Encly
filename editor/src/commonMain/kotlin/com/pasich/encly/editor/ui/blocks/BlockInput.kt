package com.pasich.encly.editor.ui.blocks

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import com.pasich.encly.editor.state.LineBreak
import com.pasich.encly.editor.state.TextDiff
import com.pasich.encly.editor.state.isLoneWebAddress
import com.pasich.encly.editor.state.normalizeLineBreaks
import com.pasich.encly.editor.state.textDiff
import com.pasich.encly.editor.state.typedShortcut
import com.pasich.encly.editor.ui.BlockActions
import kotlinx.coroutines.launch

/** Which field of a block a [BlockInput] watches: a paragraph, another text block, or a list item. */
internal sealed interface BlockField {
    /** A paragraph: its marks ("# ", "- ", ...) and a pasted lone web address convert it. */
    data object Paragraph : BlockField

    /** A heading or quote. */
    data object Styled : BlockField

    /** Item [itemId] of a list. */
    data class ListItem(val itemId: String) : BlockField

    /** Code: everything stays as typed or pasted, line breaks included. */
    data object Code : BlockField
}

/**
 * The writing rules of a block's field, applied to every edit the user makes: typing, the
 * keyboard's Enter, a paste, autocorrect. Enter arrives as a line break from every keyboard
 * (some send a key press, some commit "\n"), so this is the one place it is handled: a line
 * break typed or pasted splits the block ([BlockActions.onLineBreak]), and the field keeps only
 * the text before it. A paragraph also turns into a heading, list, quote or separator when its
 * mark is typed at its start, and into a link card when a web address alone is pasted into it.
 *
 * A "/" typed into an empty paragraph opens the slash picker ([BlockActions.onSlashTyped]); it
 * reacts to committed edits only: the picker is shown once the "/" is no longer under IME
 * composition, and nothing is converted until the user picks a command.
 *
 * The editor applies each rule at once, in one undo step. When it declines (simple editing),
 * the line break stays in the text.
 */
internal class BlockInput(
    private val field: BlockField,
    private val defer: (() -> Unit) -> Unit = { it() },
    private val actions: () -> BlockActions,
) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val old = originalText.toString()
        val new = asCharSequence().toString()
        if (old == new) return
        val cursor = if (selection.collapsed) selection.start else -1
        val diff = textDiff(old, new, cursor)
        when {
            actions().textRange.isCrossField -> replaceRange(diff.inserted)
            field == BlockField.Code -> keepVerbatim(diff)
            else -> applyRules(old, new, cursor, diff)
        }
    }

    private fun TextFieldBuffer.applyRules(old: String, new: String, cursor: Int, diff: TextDiff) {
        val lineBreak = LineBreak.of(old, diff)
        when {
            pastedCopy(old, diff) -> Unit

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

            field != BlockField.Paragraph -> Unit

            else -> {
                if (diff.removed.isEmpty()) onParagraphInsert(old, new, cursor, diff.inserted)
                // An open slash picker follows every edit, typed or deleted.
                actions().onSlashTyped(new, started = old.isEmpty() && new == SLASH && cursor == 1)
            }
        }
    }

    // A paste of exactly what the last copy or cut in the editor put on the clipboard inserts the blocks it was of.
    private fun TextFieldBuffer.pastedCopy(old: String, diff: TextDiff): Boolean {
        val itemId = (field as? BlockField.ListItem)?.itemId
        val kept = if (diff.inserted.length < 2) {
            null
        } else {
            actions().onPasteCopied(
                itemId,
                head = old.substring(0, diff.start),
                pasted = diff.inserted,
                tail = old.substring(diff.start + diff.removed.length),
            )
        }
        kept?.let {
            replace(0, length, it)
            placeCursorAtEnd()
        }
        return kept != null
    }

    // Code is never split, converted or rewritten; only a carriage return is made a line feed, as everywhere.
    private fun TextFieldBuffer.keepVerbatim(diff: TextDiff) {
        if ('\r' in diff.inserted) replace(diff.start, diff.end, normalizeLineBreaks(diff.inserted))
    }

    private fun TextFieldBuffer.replaceRange(inserted: String) = replaceSelectedRange(inserted, defer, actions())

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

/**
 * An edit of a field while a selection runs across blocks: the selection is replaced once this edit is over (it moves
 * the cursor and the text of the fields, which must not happen under an edit of one of them) and the field itself
 * keeps its text. Enter only deletes.
 */
internal fun TextFieldBuffer.replaceSelectedRange(
    inserted: String,
    defer: (() -> Unit) -> Unit,
    actions: BlockActions,
) {
    revertAllChanges()
    val typed = inserted.takeUnless { it == "\n" || it == "\r" }.orEmpty()
    defer { actions.replaceTextRange(typed) }
}

/** The character that opens the slash picker in an empty paragraph. */
private const val SLASH = "/"

/** Runs a task a moment later, on the composition's own dispatcher: after the edit that asked for it is over. */
@Composable
internal fun rememberDefer(): (() -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(scope) {
        { task ->
            scope.launch { withFrameNanos { } }.invokeOnCompletion { cause ->
                if (cause ==
                    null
                ) {
                    task()
                }
            }
        }
    }
}
