package com.pasich.encly.presentation.editor.blocks

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.pasich.encly.presentation.editor.BlockActions
import com.pasich.encly.presentation.editor.focus.KeyboardUtils
import com.pasich.encly.presentation.editor.state.BlockRemoveAction

/**
 * Keyboard handling of a text-bearing block editing [state]: Backspace in an empty block removes
 * it, Up at the start and Down at the end move to the block above/below (Down in the last block
 * adds a paragraph), and Enter runs [onEnter] when it handles it (returns true).
 */
internal fun Modifier.textBlockKeys(
    state: TextFieldState,
    blockActions: BlockActions,
    onEnter: () -> Boolean = { false },
): Modifier = onPreviewKeyEvent { event ->
    val selection = state.selection
    KeyboardUtils.handleKeyEvent(
        event = event,
        text = state.text,
        // A selection is not a cursor at either end: the field moves within it.
        cursorPosition = if (selection.collapsed) selection.start else -1,
        onBackspaceEmpty = { blockActions.onRemoveBlock(BlockRemoveAction.REMOVE_BACKSPACE) },
        onNavigateUp = { blockActions.navigateToPrevious() },
        onNavigateDown = {
            if (!blockActions.navigateToNext()) blockActions.onAddParagraph()
            true // Always handled, so no newline is typed.
        },
        onEnterPressed = onEnter,
    )
}

/** Enter in a paragraph starts a new one after it. */
internal fun BlockActions.addParagraphOnEnter(): () -> Boolean = {
    onAddParagraph()
    true
}
