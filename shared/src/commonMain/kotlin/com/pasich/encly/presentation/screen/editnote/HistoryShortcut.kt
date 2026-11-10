package com.pasich.encly.presentation.screen.editnote

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel

/** What a keyboard shortcut asks the editor for. */
internal enum class HistoryShortcut { UNDO, REDO }

/**
 * Ctrl or Cmd+Z is undo; Ctrl or Cmd+Shift+Z and Ctrl or Cmd+Y are redo; null for any other key.
 * Both the key-down and the key-up belong to the shortcut.
 */
internal fun historyShortcut(key: Key, ctrlOrMeta: Boolean, shift: Boolean): HistoryShortcut? = when {
    !ctrlOrMeta -> null
    key == Key.Z && shift -> HistoryShortcut.REDO
    key == Key.Z -> HistoryShortcut.UNDO
    key == Key.Y -> HistoryShortcut.REDO
    else -> null
}

/**
 * Hardware and keyboard-app undo and redo go to the editor's own history, which spans every block
 * (a text field's built-in undo knows only its own field). Seen before the field, so the field never
 * runs its own, but only while a text block has focus: the title, the find bar and a link's address
 * field keep their own undo. The key-up is consumed too; a locked or read-only editor does
 * nothing. True when the key was used.
 */
internal fun EditNoteViewModel.handleHistoryKey(event: KeyEvent): Boolean {
    val shortcut = historyShortcut(event.key, event.isCtrlPressed || event.isMetaPressed, event.isShiftPressed)
        ?.takeIf { ownsHistoryKeys() }
    if (shortcut != null && event.type == KeyEventType.KeyDown) {
        if (shortcut == HistoryShortcut.UNDO) undo() else redo()
    }
    return shortcut != null
}

/** Whether the focus is in a text or list block (not the title, the find bar or a link's address field). */
internal fun EditNoteViewModel.ownsHistoryKeys(): Boolean {
    val id = focusedBlockId.value ?: return false
    val block = blocks.firstOrNull { it.id == id }
    return block != null && block !is com.pasich.encly.editor.model.Block.LinkBlock
}
