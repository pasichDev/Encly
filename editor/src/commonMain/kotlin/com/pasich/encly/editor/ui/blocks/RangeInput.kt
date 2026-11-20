package com.pasich.encly.editor.ui.blocks

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import com.pasich.encly.editor.focus.CaretLine
import com.pasich.encly.editor.state.HeadMove
import com.pasich.encly.editor.state.TextPoint
import com.pasich.encly.editor.ui.BlockActions
import kotlinx.coroutines.flow.drop

/**
 * Watches the mouse over a field for a drag or a Shift+click that runs into another field, which makes a text
 * selection across blocks. It only looks (events are seen first and never consumed), so the field's own selection
 * goes on; a touch is left to the selection handles.
 */
internal fun Modifier.rangeMouse(field: FieldHighlight): Modifier = pointerInput(field) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.type != PointerType.Mouse) return@awaitEachGesture
        field.beginMouse(down.position, shift = currentEvent.keyboardModifiers.isShiftPressed)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.firstOrNull { it.id == down.id }?.let { field.dragMouse(it.position) }
        } while (event.changes.any { it.pressed })
        field.endMouse()
    }
}

/**
 * Keeps the text selection across blocks in step with what the field itself has selected: a selection of the
 * field's own (a long press, a drag, Ctrl+A, Shift+arrows) is the whole selection until it runs into another
 * field, and its handles and tint are drawn the same way. Also lets the selection reach the field's own state, so a
 * handle dragged inside the field moves the field's selection.
 */
@Composable
internal fun BindFieldRange(state: TextFieldState, blockActions: BlockActions, highlight: FieldHighlight) {
    val actions by rememberUpdatedState(blockActions)
    DisposableEffect(state, highlight) {
        highlight.state = state
        onDispose { if (highlight.state === state) highlight.state = null }
    }
    // The first value is where the field starts, not something the user selected.
    LaunchedEffect(state, highlight) {
        snapshotFlow {
            state.selection
        }.drop(1).collect { selection -> highlight.onSelection(selection, actions.textRange) }
    }
}

/** What the field does with [event] while a text selection across blocks exists or may start; true if it took it. */
internal fun rangeKey(event: KeyEvent, field: RangeField, actions: BlockActions): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val range = actions.textRange
    val command = event.isCtrlPressed || event.isMetaPressed
    val move = if (!event.isShiftPressed) {
        null
    } else if (command) {
        event.key.noteMove()
    } else {
        event.key.headMove(field.rtl)
    }
    return if (move != null) {
        range.extend(move, field.anchor, field.caret, onEdgeLine = field.edge(move))
    } else {
        range.isCrossField && crossKey(event, command, actions)
    }
}

/** What the keys do over a selection that runs across fields; true if one was taken. */
private fun crossKey(event: KeyEvent, command: Boolean, actions: BlockActions): Boolean = when {
    event.key == Key.Escape -> true.also { actions.textRange.clear() }
    event.key == Key.Delete || event.key == Key.Backspace || event.key == Key.Enter -> actions.replaceTextRange("")
    command && event.key == Key.C -> actions.copyTextRange(markdown = event.isShiftPressed)
    command && event.key == Key.X -> actions.cutTextRange()
    else -> false
}

/** What a field tells [rangeKey] about itself: its selection as points, its direction and its cursor's line. */
internal class RangeField(val anchor: TextPoint, val caret: TextPoint, val rtl: Boolean, private val line: CaretLine?) {
    /** Whether the cursor is on the line that Up (or Down) would leave the field from. */
    fun edge(move: HeadMove): Boolean = if (move == HeadMove.UP) line?.onFirst == true else line?.onLast == true
}

/** The field's selection as points, from its text state [selection] in the field [itemId] of block [blockId]. */
internal fun rangeField(blockId: String, itemId: String?, selection: TextRange, line: CaretLine?) = RangeField(
    anchor = TextPoint(blockId, selection.start, itemId),
    caret = TextPoint(blockId, selection.end, itemId),
    rtl = line?.rtl == true,
    line = line,
)

/** The move that [this] key makes the end of a selection make, with Shift held; null for any other key. */
internal fun Key.headMove(rtl: Boolean): HeadMove? = when (this) {
    Key.DirectionLeft -> if (rtl) HeadMove.NEXT else HeadMove.PREVIOUS
    Key.DirectionRight -> if (rtl) HeadMove.PREVIOUS else HeadMove.NEXT
    Key.DirectionUp -> HeadMove.UP
    Key.DirectionDown -> HeadMove.DOWN
    Key.MoveHome -> HeadMove.FIELD_START
    Key.MoveEnd -> HeadMove.FIELD_END
    Key.PageUp -> HeadMove.PAGE_UP
    Key.PageDown -> HeadMove.PAGE_DOWN
    else -> null
}

/** The move of Ctrl or Cmd+Shift+Home and End: the start and the end of the note; null for any other key. */
internal fun Key.noteMove(): HeadMove? = when (this) {
    Key.MoveHome -> HeadMove.NOTE_START
    Key.MoveEnd -> HeadMove.NOTE_END
    else -> null
}
