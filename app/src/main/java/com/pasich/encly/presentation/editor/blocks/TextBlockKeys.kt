package com.pasich.encly.presentation.editor.blocks

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.pasich.encly.presentation.editor.BlockActions
import com.pasich.encly.presentation.editor.focus.KeyboardUtils
import com.pasich.encly.presentation.editor.state.BlockRemoveAction

/**
 * Keyboard handling of a text-bearing block holding [text]: Backspace in an empty block removes
 * it, Up/Down move to the block above/below (Down in the last block adds a paragraph), and
 * Enter adds a paragraph when [enterAddsParagraph].
 */
internal fun Modifier.textBlockKeys(text: String, blockActions: BlockActions, enterAddsParagraph: Boolean): Modifier =
    onPreviewKeyEvent { event ->
        KeyboardUtils.handleKeyEvent(
            event = event,
            text = text,
            onBackspaceEmpty = { blockActions.onRemoveBlock(BlockRemoveAction.REMOVE_BACKSPACE) },
            onNavigateUp = { blockActions.navigateToPrevious() },
            onNavigateDown = {
                if (!blockActions.navigateToNext()) blockActions.onAddParagraph()
                true // Always handled, so no newline is typed.
            },
            onEnterPressed = {
                if (enterAddsParagraph) blockActions.onAddParagraph()
                enterAddsParagraph
            },
        )
    }
