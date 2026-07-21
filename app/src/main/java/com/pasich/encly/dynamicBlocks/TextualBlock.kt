package com.pasich.encly.dynamicBlocks

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Interface for blocks that contain text.
 * Used for uniform handling of text blocks of different types.
 */
interface TextualBlock {
    val text: MutableStateFlow<String>
    
    /**
     * Checks whether the block's text is empty.
     */
    fun isEmpty(): Boolean = text.value.isEmpty()
    
    /**
     * Appends text to the end.
     */
    fun appendText(addition: String) {
        text.value = text.value + addition
    }
}
