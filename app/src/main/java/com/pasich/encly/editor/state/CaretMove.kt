package com.pasich.encly.editor.state

/**
 * A cursor key that would leave the field it is pressed in: Up from its first line, Down from its
 * last, Left from its start, Right from its end. [Up] and [Down] carry the cursor's horizontal
 * position ([x], null when unknown) so the cursor keeps its column in the next field.
 */
sealed interface CaretMove {
    data class Up(val x: Float?) : CaretMove
    data class Down(val x: Float?) : CaretMove
    data object Left : CaretMove
    data object Right : CaretMove
}

/** Whether [this] move goes to the block above. */
val CaretMove.goesUp: Boolean get() = this is CaretMove.Up || this == CaretMove.Left

/**
 * The request that focuses [blockId] after [this] move: up and left arrive from below, at the
 * column the cursor had (up) or at the end (left); down and right arrive from above, at the column
 * (down) or at the start (right).
 */
fun CaretMove.requestFor(blockId: String): FocusRequest = when (this) {
    is CaretMove.Up -> x?.let { FocusRequest(blockId, caretX = it, fromBelow = true) }
        ?: FocusRequest(blockId, cursorToEnd = true, fromBelow = true)

    is CaretMove.Down -> x?.let { FocusRequest(blockId, caretX = it) } ?: FocusRequest(blockId, caret = 0)

    CaretMove.Left -> FocusRequest(blockId, cursorToEnd = true, fromBelow = true)

    CaretMove.Right -> FocusRequest(blockId, caret = 0)
}
