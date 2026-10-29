package com.pasich.encly.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
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
