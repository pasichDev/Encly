package com.pasich.encly.editor.model

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Interface for blocks that contain text.
 * Used for uniform handling of text blocks of different types.
 */
public interface TextualBlock {
    /** The text of the block, live. */
    public val text: MutableStateFlow<String>
}

/** Whether the block's text is empty. */
internal fun TextualBlock.isEmpty(): Boolean = text.value.isEmpty()
