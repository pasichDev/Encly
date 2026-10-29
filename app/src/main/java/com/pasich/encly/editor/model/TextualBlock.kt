package com.pasich.encly.editor.model

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Interface for blocks that contain text.
 * Used for uniform handling of text blocks of different types.
 */
interface TextualBlock {
    val text: MutableStateFlow<String>
}

/** Whether the block's text is empty. */
fun TextualBlock.isEmpty(): Boolean = text.value.isEmpty()
