package com.pasich.encly.editor.ui.blocks

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import com.pasich.encly.editor.ui.BlockActions

/** Whether [this] is Ctrl or Cmd+A going down. */
internal fun KeyEvent.isSelectAll(): Boolean =
    isSelectAllKey(key, type, command = isCtrlPressed || isMetaPressed, shift = isShiftPressed)

/** Whether [key] going [type] is Select all: A with Ctrl or Cmd ([command]) and no Shift. */
internal fun isSelectAllKey(key: Key, type: KeyEventType, command: Boolean, shift: Boolean): Boolean =
    type == KeyEventType.KeyDown && key == Key.A && command && !shift

/** Whether [selection] covers a field's whole text of [length] (an empty field is covered by any cursor). */
internal fun coversWholeText(selection: TextRange, length: Int): Boolean =
    length == 0 || (selection.min == 0 && selection.max == length)

/**
 * Select all pressed twice: the first press is the field's own (it selects its text), the second, with
 * [selection] already covering all of the field's text of [length], selects the whole note as text.
 * True when this took the key.
 */
internal fun selectAllTextKey(event: KeyEvent, selection: TextRange, length: Int, actions: BlockActions): Boolean =
    event.isSelectAll() && coversWholeText(selection, length) && actions.selectAllText()
