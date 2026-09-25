package com.pasich.encly.presentation.editor.blocks

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.pasich.encly.presentation.editor.BlockActions
import com.pasich.encly.presentation.editor.focus.KeyboardUtils
import com.pasich.encly.presentation.editor.state.BlockRemoveAction

/**
 * Keyboard handling of a text-bearing block editing [state]: Backspace at the start joins the
 * block to the one above (a heading or quote first becomes a paragraph), Backspace in an empty
 * paragraph removes it, Up at the start and Down at the end move to the block above/below (Down
 * in the last block adds a paragraph). Enter is left to the field: its line break reaches
 * [BlockInput], from a hardware key and a soft keyboard alike.
 */
internal fun Modifier.textBlockKeys(state: TextFieldState, blockActions: BlockActions): Modifier =
    onPreviewKeyEvent { event ->
        val selection = state.selection
        KeyboardUtils.handleKeyEvent(
            event = event,
            text = state.text,
            // A selection is not a cursor at either end: the field moves within it.
            cursorPosition = if (selection.collapsed) selection.start else -1,
            onBackspaceEmpty = { blockActions.onRemoveBlock(BlockRemoveAction.REMOVE_BACKSPACE) },
            onBackspaceAtStart = { blockActions.onBackspaceAtStart() },
            onNavigateUp = { blockActions.navigateToPrevious() },
            onNavigateDown = {
                if (!blockActions.navigateToNext()) blockActions.onAddParagraph()
                true // Always handled, so no newline is typed.
            },
        )
    }
