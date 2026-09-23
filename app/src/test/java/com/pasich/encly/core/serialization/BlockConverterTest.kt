package com.pasich.encly.core.serialization

import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
            listOf("Completed item" to true, "Open item" to false),
            checklist.items.value.map { it.value to it.isCheck },
        )
    }

    @Test
    fun legacyListPayloadDefaultsToUncheckedChecklist() {
        val restored = BlockConverter.jsonToBlocks(
            """[{"blockType":"LIST","items":[{"value":"Legacy item"}]}]""",
        )

        val checklist = restored.single() as Block.ListBlock
        assertEquals(BlockType.LIST_CHECK, checklist.blockType)
        assertEquals(listOf("Legacy item" to false), checklist.items.value.map { it.value to it.isCheck })
    }

    @Test
    fun unreadableContentIsNullInsteadOfAnException() {
        listOf(
            "{not-json",
            "[{\"blockType\":\"FUTURE_BLOCK\"}]",
            "[{\"text\":\"no type\"}]",
            "[1, 2]",
            "{\"blockType\":\"TEXT\"}",
        ).forEach { json ->
            assertNull(json, BlockConverter.jsonToBlocksOrNull(json))
        }
    }

    @Test
    fun blocksWithoutContentAreNotStoredAndNoNullIsWritten() {
        val json = BlockConverter.blocksToJson(
            listOf(
                Block.TextBlock(MutableStateFlow("kept")),
                Block.TextBlock(MutableStateFlow("   ")),
                Block.QuoteBlock(MutableStateFlow("")),
                Block.ListBlock(MutableStateFlow(listOf(ItemListBlock(" "))), BlockType.LIST_CHECK),
                Block.LinkBlock(),
                Block.SeparatorBlock(),
            ),
        )

        assertFalse(json.contains("null"))
        val restored = BlockConverter.jsonToBlocks(json)
        assertEquals(2, restored.size)
        assertEquals("kept", (restored[0] as Block.TextBlock).text.value)
        assertTrue(restored[1] is Block.SeparatorBlock)
    }

    @Test
    fun nullEntriesAreDropped() {
        val restored = BlockConverter.jsonToBlocks("[null, {\"blockType\":\"TEXT\",\"text\":\"kept\"}]")

        assertEquals("kept", (restored.single() as Block.TextBlock).text.value)
    }

    @Test
    fun blockIdsAreNotStored() {
        val json = BlockConverter.blocksToJson(
            listOf(
                Block.TextBlock(MutableStateFlow("t")),
                Block.ListBlock(MutableStateFlow(listOf(ItemListBlock("i"))), BlockType.LIST_CHECK),
            ),
        )

        assertFalse(json, json.contains("\"id\""))
    }
}
