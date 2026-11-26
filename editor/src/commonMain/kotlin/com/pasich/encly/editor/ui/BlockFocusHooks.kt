package com.pasich.encly.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import com.pasich.encly.editor.LocalEditorLogger
import com.pasich.encly.editor.focus.BlockFocusRegistry
import com.pasich.encly.editor.model.Block

/** The focus registry of one editor showing [blocks] (its live block list). */
@Composable
public fun rememberBlockFocusRegistry(blocks: List<Block>): BlockFocusRegistry {
    val logger = LocalEditorLogger.current
    return remember(blocks, logger) { BlockFocusRegistry(logger) { blocks } }
}

/** Registers [focusRequester], the field of block [blockId], while it is composed. */
@Composable
internal fun RegisterFocusRequester(blockId: String, registry: BlockFocusRegistry, focusRequester: FocusRequester) {
    DisposableEffect(blockId, registry, focusRequester) {
        val unregister = registry.registerFocusTarget(blockId) { focusRequester.requestFocus() }
        onDispose { unregister() }
    }
}

/**
 * What a touch in a list does: it makes the list the block the toolbar acts on ([onInteract]) and, where [dropsFocus],
 * takes the focus away from whatever field has it. The text of a locked checklist takes no focus, so the field that
 * had it would go on blinking its caret as if the touch had done nothing.
 */
@Composable
internal fun rememberListTouch(onInteract: () -> Unit, dropsFocus: Boolean): () -> Unit {
    val focusManager = LocalFocusManager.current
    val touched by rememberUpdatedState {
        onInteract()
        if (dropsFocus) focusManager.clearFocus()
    }
    return remember { { touched() } }
}
