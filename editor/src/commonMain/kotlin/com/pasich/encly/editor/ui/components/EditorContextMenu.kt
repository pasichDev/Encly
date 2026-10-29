package com.pasich.encly.editor.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize

/**
 * A menu of [entries] that opens at the pointer on a right click on [content], or at its corner on the
 * Menu key / Shift+F10 while it holds keyboard focus. It is a Material menu: themed, closed by Esc or a
 * click outside, its rows take the arrow keys, Enter and hover. A finger never opens it. With
 * [claimPress] the menu takes the right click before its children (a button inside would otherwise act on
 * it); without, a right click a child already used (a text field's own cut / copy / paste menu) is left to
 * that child. With [enabled] false the content is shown as is.
 */
@Composable
internal fun EditorContextMenu(
    entries: () -> List<ContextEntry>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    claimPress: Boolean = false,
    content: @Composable () -> Unit,
) {
    var anchor by remember { mutableStateOf<Offset?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    // One lambda for the life of the menu, so the pointer loop is never restarted.
    val open = remember { { position: Offset -> anchor = position } }
    val density = LocalDensity.current
    Box(modifier = modifier.onSizeChanged { size = it }.contextMenuTriggers(enabled, claimPress, open)) {
        content()
        anchor?.let { position ->
            MenuPopup(
                entries = remember(position) { entries() },
                // A menu is placed from the bottom start of its anchor.
                offset = with(density) { DpOffset(position.x.toDp(), (position.y - size.height).toDp()) },
                onDismiss = { anchor = null },
            )
        }
    }
}

@Composable
private fun MenuPopup(entries: List<ContextEntry>, offset: DpOffset, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        offset = offset,
        // Esc closes the menu from any row, also where the platform's popup does not do it itself.
        modifier = Modifier.onKeyEvent { event ->
            (event.key == Key.Escape && event.type == KeyEventType.KeyDown).also { if (it) onDismiss() }
        },
    ) {
        entries.forEach { entry ->
            DropdownMenuItem(
                text = { Text(entry.label, style = MaterialTheme.typography.labelLarge) },
                onClick = {
                    onDismiss()
                    entry.onClick()
                },
                leadingIcon = { Icon(entry.icon, contentDescription = null) },
                colors = if (entry.destructive) {
                    MenuDefaults.itemColors(textColor = colors.error, leadingIconColor = colors.error)
                } else {
                    MenuDefaults.itemColors()
                },
                modifier = Modifier.editorInteractive(),
            )
        }
    }
}

/** Where the menu opens for the keyboard: just inside the top start corner of the content, in pixels. */
private val KeyboardMenuAnchor = Offset(x = 24f, y = 24f)

private fun Modifier.contextMenuTriggers(enabled: Boolean, claimPress: Boolean, open: (Offset) -> Unit): Modifier =
    if (enabled) {
        this
            .secondaryPress(if (claimPress) PointerEventPass.Initial else PointerEventPass.Final, open)
            .onKeyEvent { event ->
                val menuKey = event.key == Key.Menu || (event.key == Key.F10 && event.isShiftPressed)
                (menuKey && event.type == KeyEventType.KeyDown).also { if (it) open(KeyboardMenuAnchor) }
            }
    } else {
        this
    }

/**
 * Calls [open] with the position of a secondary-button press, seen in [pass] (the initial pass is before
 * the children, the final one after them); a press that is used here is consumed, so it starts nothing else.
 */
private fun Modifier.secondaryPress(pass: PointerEventPass, open: (Offset) -> Unit): Modifier =
    pointerInput(pass, open) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(pass)
                val press = event.changes.firstOrNull()
                if (press != null && event.isSecondaryPress() && press.changedToDown()) {
                    press.consume()
                    open(press.position)
                }
            }
        }
    }

private fun PointerEvent.isSecondaryPress() = type == PointerEventType.Press && buttons.isSecondaryPressed
