package com.pasich.encly.editor.codec

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Nested lists in the stored JSON and in Markdown, in both directions. */
class NestedListCodecTest {
    private fun row(text: String, level: Int = 0, kind: BlockType? = null, check: Boolean = false) =
        ItemListBlock(text, isCheck = check, level = level, kind = kind)

    private fun list(type: BlockType, vararg rows: ItemListBlock) =
        Block.ListBlock(MutableStateFlow(rows.toList()), type)

    /** A list as `kind:text level` pieces, for exact assertions. */
    private fun shape(block: Block): String {
        val list = block as Block.ListBlock
        val kind = list.blockType.name.removePrefix("LIST_").lowercase()
        return kind + ":" + list.items.value.joinToString("|") { item ->
            val own = item.kind?.name?.removePrefix("LIST_")?.lowercase()?.let { "<$it>" }.orEmpty()
            "${if (item.isCheck) "x" else ""}${item.value}${item.level}$own"
        }
    }

    private fun markdown(text: String) = MarkdownBlockMapper.toBlocks(text).map(::shape)

    // --- JSON -------------------------------------------------------------------------------------

    @Test
    fun aFlatListIsStoredExactlyAsBefore() {
        val json = BlockConverter.blocksToJson(
            listOf(list(BlockType.LIST_CHECK, row("milk"), row("eggs", check = true))),
        )

        assertEquals(
            """
            [
              {
                "blockType": "LIST_CHECK",
                "items": [
                  {
                    "value": "milk",
                    "isCheck": false
                  },
                  {
                    "value": "eggs",
                    "isCheck": true
                  }
                ]
              }
            ]
            """.trimIndent(),
            json,
        )
        assertFalse("level" in json)
        assertFalse("kind" in json)
    }

    @Test
    fun aNestedItemStoresItsLevelAfterItsCheckAndAKindOnlyWhenItDiffers() {
        val json = BlockConverter.blocksToJson(
            listOf(
                list(
                    BlockType.LIST_BULLET,
                    row("a"),
                    row("b", 1),
                    row("c", 2, BlockType.LIST_CHECK, check = true),
                    row("d", 1, BlockType.LIST_BULLET),
                ),
            ),
        )

        assertEquals(
            """
            [
              {
                "blockType": "LIST_BULLET",
                "items": [
                  {
                    "value": "a",
                    "isCheck": false
                  },
                  {
                    "value": "b",
                    "isCheck": false,
                    "level": 1
                  },
                  {
                    "value": "c",
                    "isCheck": true,
                    "level": 2,
                    "kind": "LIST_CHECK"
                  },
                  {
                    "value": "d",
                    "isCheck": false,
                    "level": 1
                  }
                ]
              }
            ]
            """.trimIndent(),
            json,
        )
    }

    @Test
    fun nestedItemsComeBackAsTheyWereStored() {
        val original = listOf(
            list(BlockType.LIST_NUMBER, row("a"), row("b", 1, BlockType.LIST_CHECK), row("c", 2), row("d")),
        )

        val back = BlockConverter.jsonToBlocks(BlockConverter.blocksToJson(original))

        assertEquals(listOf("number:a0|b1<check>|c2|d0"), back.map(::shape))
        assertEquals(BlockConverter.blocksToJson(original), BlockConverter.blocksToJson(back))
    }

    @Test
    fun aBlankItemIsDroppedAndItsChildrenAreBroughtUpToAValidLevel() {
        val json = BlockConverter.blocksToJson(listOf(list(BlockType.LIST_BULLET, row(" "), row("child", 1), row("x"))))

        val back = BlockConverter.jsonToBlocks(json)

        assertEquals(listOf("bullet:child0|x0"), back.map(::shape))
        assertFalse("level" in json)
    }

    @Test
    fun levelsThatAreNotValidAreMadeValidWhenAStoredNoteIsRead() {
        val json = """[{"blockType":"LIST_BULLET","items":[
            {"value":"a","isCheck":false,"level":3},
            {"value":"b","isCheck":false,"level":"2"},
            {"value":"c","isCheck":false,"level":"deep"},
            {"value":"d","isCheck":false,"level":-1,"kind":"QUOTE"},
            {"value":"e","isCheck":false,"level":1,"kind":"LIST_BULLET"},
            {"value":"f","isCheck":false,"level":1,"kind":"NOT_A_KIND"}
        ]}]"""

        val back = BlockConverter.jsonToBlocks(json)

        assertEquals(listOf("bullet:a0|b1|c0|d0|e1|f1"), back.map(::shape))
    }

    @Test
    fun aNoteWithoutLevelsReadsFlat() {
        val json = """[{"blockType":"LIST_CHECK","items":[
            {"value":"a","isCheck":true},{"value":"b","isCheck":false}
        ]}]"""

        assertEquals(listOf("check:xa0|b0"), BlockConverter.jsonToBlocks(json).map(::shape))
    }

    @Test
    fun aReaderTakesTheKeysItKnowsAndIgnoresTheRest() {
        // How an app without nesting reads a nested note: `level` and `kind` are keys it does not know. This is the
        // same tolerance, tested with a key nothing knows: every item comes back, in order, with its text and check.
        val json = """[{"blockType":"LIST_BULLET","items":[
            {"value":"a","isCheck":true,"future":{"x":1}},
            {"value":"b","isCheck":false,"level":1,"kind":"LIST_CHECK","other":[1,2]}
        ]}]"""

        val read = BlockConverter.jsonToBlocks(json).single() as Block.ListBlock

        assertEquals(listOf("a", "b"), read.items.value.map { it.value })
        assertEquals(listOf(true, false), read.items.value.map { it.isCheck })
    }

    // --- Markdown in ------------------------------------------------------------------------------

    @Test
    fun itemsIndentedByTwoSpacesNestUnderTheOneAbove() {
        assertEquals(
            listOf("bullet:a0|b1|c2|d1|e0"),
            markdown("- a\n  - b\n    - c\n  - d\n- e"),
        )
    }

    @Test
    fun itemsIndentedByFourSpacesOrATabNestToo() {
        assertEquals(listOf("bullet:a0|b1|c2|d0"), markdown("- a\n    - b\n        - c\n- d"))
        assertEquals(listOf("bullet:a0|b1|c2|d0"), markdown("- a\n\t- b\n\t\t- c\n- d"))
        assertEquals(listOf("bullet:a0|b1|c2"), markdown("- a\n \t- b\n  \t  - c"))
    }

    @Test
    fun anUnevenIndentStillNestsOneLevelAtATime() {
        assertEquals(listOf("bullet:a0|b1|c2|d1|e0"), markdown("- a\n   - b\n          - c\n   - d\n- e"))
    }

    @Test
    fun nestingDeeperThanThreeLevelsStaysAtTheThird() {
        assertEquals(listOf("bullet:a0|b1|c2|d2"), markdown("- a\n  - b\n    - c\n      - d"))
    }

    @Test
    fun aNestedListMayBeOfAnotherKindThanItsParent() {
        assertEquals(
            listOf("bullet:a0|b1<number>|c1<number>|d1<check>|e0"),
            markdown("- a\n  1. b\n  2. c\n  - [ ] d\n- e"),
        )
    }

    @Test
    fun aNumberedListWithNestedBulletsAndChecks() {
        assertEquals(
            listOf("number:a0|b1<bullet>|xc1<check>|d0"),
            markdown("1. a\n   - b\n   - [x] c\n2. d"),
        )
    }

    @Test
    fun anotherKindAtTheTopStartsAListOfItsOwn() {
        assertEquals(listOf("bullet:a0|b1", "number:c0|d1"), markdown("- a\n  - b\n1. c\n   2. d"))
    }

    @Test
    fun aNestedItemOfTheListsOwnKindStoresNoKind() {
        val blocks = MarkdownBlockMapper.toBlocks("- a\n  - b")

        assertEquals(listOf(null, null), (blocks.single() as Block.ListBlock).items.value.map { it.kind })
    }

    @Test
    fun anIndentedFirstItemStartsAtTheTop() {
        assertEquals(listOf("bullet:a0|b0|c1"), markdown("  - a\n  - b\n    - c"))
    }

    @Test
    fun aBlankNestedItemIsDroppedWithoutLeavingOrphans() {
        assertEquals(listOf("bullet:a0|c1"), markdown("- a\n  - \n    - c"))
        assertEquals(listOf("bullet:a0|x0"), markdown("- \n  - a\n- x"))
    }

    @Test
    fun aParagraphEndsTheListAndTheIndentStartsAgain() {
        assertEquals(
            listOf("bullet:a0|b1", "text", "bullet:c0"),
            MarkdownBlockMapper.toBlocks("- a\n  - b\n\ntext\n\n  - c")
                .map { if (it is Block.ListBlock) shape(it) else "text" },
        )
    }

    @Test
    fun indentedTextAfterAnItemStillContinuesIt() {
        val blocks = MarkdownBlockMapper.toBlocks("- a\n  - b\n  more of b")

        assertEquals(listOf("a", "b more of b"), (blocks.single() as Block.ListBlock).items.value.map { it.value })
    }

    @Test
    fun aPastedNestedListKeepsItsLevels() {
        val blocks = MarkdownBlockMapper.pasteBlocks("- a\n  - b\n  - c")

        assertTrue(blocks.single() is Block.ListBlock)
        assertEquals("bullet:a0|b1|c1", shape(blocks.single()))
    }

    @Test
    fun checklistsNestToo() {
        assertEquals(
            listOf("check:a0|xb1|c1"),
            markdown("- [ ] a\n  - [x] b\n  - [ ] c"),
        )
    }
}
