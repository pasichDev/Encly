package com.pasich.encly.dynamicBlocks

import androidx.compose.ui.graphics.Color
import com.pasich.encly.R
import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.domain.model.LinkDataBlock
import kotlinx.coroutines.flow.MutableStateFlow

enum class BlockType {
    TEXT, LIST_NUMBER, LIST_CHECK, QUOTE, SEPARATOR, LINK, H1, H2, H3, H4

}

sealed class Block {
    data class TextBlock(
        override var text: MutableStateFlow<String> = MutableStateFlow(""),
        var placeholder: Int = R.string.text
    ) : Block(), TextualBlock

    data class HBlock(
        override var text: MutableStateFlow<String> = MutableStateFlow(""), val blockType: BlockType
    ) : Block(), TextualBlock

    data class QuoteBlock(
        override var text: MutableStateFlow<String> = MutableStateFlow("")
    ) : Block(), TextualBlock

    data class LinkBlock(
        var block: MutableStateFlow<LinkDataBlock> = MutableStateFlow(
            LinkDataBlock()
        )
    ) : Block()

    data class SeparatorBlock(@Transient var color: Color = Color.Gray) : Block()

    data class ListBlock(
        var items: MutableStateFlow<List<ItemListBlock>> = MutableStateFlow(
            listOf(
                ItemListBlock("")
            )
        ), val blockType: BlockType
    ) : Block()
}


