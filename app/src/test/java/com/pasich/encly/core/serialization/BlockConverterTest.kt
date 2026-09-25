package com.pasich.encly.core.serialization

import com.google.gson.JsonParser
import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.domain.model.LinkDataBlock
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
    fun aBulletedListKeepsItsKind() {
        val source = listOf(
            Block.ListBlock(
                MutableStateFlow(listOf(ItemListBlock("milk"), ItemListBlock("bread"))),
                BlockType.LIST_BULLET,
            ),
        )

        val restored = BlockConverter.jsonToBlocks(BlockConverter.blocksToJson(source)).single() as Block.ListBlock

        assertEquals(BlockType.LIST_BULLET, restored.blockType)
        assertEquals(listOf("milk", "bread"), restored.items.value.map { it.value })
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

    /**
     * Pins the stored note format key by key. Notes written by older versions must stay
     * readable, and the release build must write the same keys as the debug build: none of
     * them may come from a (renamable) field name.
     */
    @Test
    fun storedFormatUsesFixedKeyNames() {
        val json = BlockConverter.blocksToJson(
            listOf(
                Block.TextBlock(MutableStateFlow("t")),
                Block.HBlock(MutableStateFlow("h"), BlockType.H2),
                Block.QuoteBlock(MutableStateFlow("q")),
                Block.LinkBlock(MutableStateFlow(LinkDataBlock(url = "u", title = "ti", imageUrl = "i"))),
                Block.SeparatorBlock(),
                Block.ListBlock(
                    MutableStateFlow(listOf(ItemListBlock("a", isCheck = true), ItemListBlock(" "))),
                    BlockType.LIST_NUMBER,
                ),
            ),
        )

        val expected = """
            [
              {"blockType":"TEXT","text":"t"},
              {"blockType":"H2","text":"h"},
              {"blockType":"QUOTE","text":"q"},
              {"blockType":"LINK","block":{"url":"u","title":"ti","imageUrl":"i"}},
              {"blockType":"SEPARATOR"},
              {"blockType":"LIST_NUMBER","items":[{"value":"a","isCheck":true}]}
            ]
        """
        assertEquals(JsonParser.parseString(expected), JsonParser.parseString(json))
    }
}
