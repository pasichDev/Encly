package com.pasich.encly.editor.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.pasich.encly.editor.state.SelectedBlocks

/** What a hardware key does while whole blocks are selected. */
internal enum class SelectionKey { LEAVE, EXTEND_UP, EXTEND_DOWN, SELECT_ALL, COPY, COPY_MARKDOWN, CUT, DELETE }

/** The selection key [this] is going down, null for any other key (and for a key coming up). */
internal fun KeyEvent.selectionKey(): SelectionKey? =
    selectionKeyOf(key, type, command = isCtrlPressed || isMetaPressed, shift = isShiftPressed)

/** The selection key for [key] going [type], with Ctrl or Cmd held as [command] and Shift as [shift]; null for none. */
internal fun selectionKeyOf(key: Key, type: KeyEventType, command: Boolean, shift: Boolean): SelectionKey? {
    if (type != KeyEventType.KeyDown) return null
    return when (key) {
        Key.Escape -> SelectionKey.LEAVE
        Key.Delete, Key.Backspace -> SelectionKey.DELETE
        Key.DirectionUp -> SelectionKey.EXTEND_UP.takeIf { shift }
        Key.DirectionDown -> SelectionKey.EXTEND_DOWN.takeIf { shift }
        Key.A -> SelectionKey.SELECT_ALL.takeIf { command }
        Key.X -> SelectionKey.CUT.takeIf { command }
        Key.C -> (if (shift) SelectionKey.COPY_MARKDOWN else SelectionKey.COPY).takeIf { command }
        else -> null
    }
}

/**
 * Carries out [key] on [selected]: Escape leaves, Shift+Up and Down extend the range, Ctrl or Cmd+A picks every
 * block, and copy, cut and delete go to [actions] (cut and delete only when the note can be changed).
 */
internal fun SelectionKey.perform(selected: SelectedBlocks, actions: SelectedBlocksActions) {
    when (this) {
        SelectionKey.LEAVE -> selected.clear()
        SelectionKey.EXTEND_UP -> selected.extend(up = true)
        SelectionKey.EXTEND_DOWN -> selected.extend(up = false)
        SelectionKey.SELECT_ALL -> selected.selectAll()
        SelectionKey.COPY -> actions.onCopy()
        SelectionKey.COPY_MARKDOWN -> actions.onCopyMarkdown()
        SelectionKey.CUT -> if (actions.canEdit) actions.onCut()
        SelectionKey.DELETE -> if (actions.canEdit) actions.onDelete()
    }
}
