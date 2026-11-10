package com.pasich.encly.editor.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Whether [event] moves the focused block: Ctrl or ⌘ with Shift and the Up arrow moves it up
 * (returns true), the Down arrow moves it down (false), anything else is null. The chord replaces the
 * text field's "select to the start / end", which a block editor has no use for: the selection ends at
 * the block anyway. Plain arrows, Alt and the plain Shift+arrows are left to the field.
 */
internal fun blockMoveDirection(event: KeyEvent): Boolean? {
    val chord = event.type == KeyEventType.KeyDown &&
        (event.isCtrlPressed || event.isMetaPressed) && event.isShiftPressed && !event.isAltPressed
    return when {
        !chord -> null
        event.key == Key.DirectionUp -> true
        event.key == Key.DirectionDown -> false
        else -> null
    }
}
