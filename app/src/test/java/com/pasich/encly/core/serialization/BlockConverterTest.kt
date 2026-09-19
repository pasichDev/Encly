package com.pasich.encly.core.serialization

import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockConverterTest {

    @Test
    fun roundTripPreservesHeadingLevelAndChecklistState() {
        val source = listOf(
            Block.HBlock(
                text = MutableStateFlow("Roadmap"),
                blockType = BlockType.H3,
            ),
            Block.ListBlock(
                items = MutableStateFlow(
                    listOf(
                        ItemListBlock("Completed item", isCheck = true),
                        ItemListBlock("Open item", isCheck = false),
                    ),
                ),
                blockType = BlockType.LIST_CHECK,
            ),
        )

        val restored = BlockConverter.jsonToBlocks(BlockConverter.blocksToJson(source))

        val heading = restored[0] as Block.HBlock
        val checklist = restored[1] as Block.ListBlock
        assertEquals(BlockType.H3, heading.blockType)
        assertEquals(
            listOf(
                ItemListBlock("Completed item", isCheck = true),
                ItemListBlock("Open item", isCheck = false),
            ),
            checklist.items.value,
        )
    }

    @Test
    fun legacyListPayloadDefaultsToUncheckedChecklist() {
        val restored = BlockConverter.jsonToBlocks(
            """[{"blockType":"LIST","items":[{"value":"Legacy item"}]}]""",
        )

        val checklist = restored.single() as Block.ListBlock
        assertEquals(BlockType.LIST_CHECK, checklist.blockType)
        assertEquals(listOf(ItemListBlock("Legacy item")), checklist.items.value)
    }
}
