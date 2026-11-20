package com.pasich.encly.editor.codec

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.TableData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Code and table blocks in the stored JSON, and as Markdown. Notes that hold none are untouched (see
 * [BlockJsonFormatTest]): the new blocks add their own `blockType` values and keys, nothing else.
 */
class TablesAndCodeCodecTest {
    private fun describe(blocks: List<Block>): List<String> = blocks.map { block ->
        when (block) {
            is Block.CodeBlock -> "code:${block.text.value}"

            is Block.TableBlock -> "table${if (block.table.value.header) "" else "-headless"}:" +
                block.table.value.rows.joinToString("/") { row -> row.cells.joinToString("|") { it.value } }

            is Block.TextBlock -> "text:${block.text.value}"

            is Block.ListBlock -> "list:${block.items.value.joinToString("|") { it.value }}"

            is Block.HBlock -> "h:${block.text.value}"

            else -> block::class.simpleName.orEmpty()
        }
    }

    private fun md(markdown: String) = describe(MarkdownBlockMapper.toBlocks(markdown))

    // --- the stored JSON ----------------------------------------------------------------------------

    @Test
    fun aCodeBlockIsStoredAsItsTextUnderItsOwnType() {
        val json = BlockConverter.blocksToJson(listOf(Block.CodeBlock(MutableStateFlow("  if (a < b) {\n\tx()\n}"))))

        assertTrue("\"blockType\": \"CODE\"" in json, json)
        assertEquals(
            listOf("code:  if (a < b) {\n\tx()\n}"),
            describe(BlockConverter.jsonToBlocks(json)),
        )
    }

    @Test
    fun aTableIsStoredAsRowsOfCellTexts() {
        val data = TableData.of(listOf(listOf("a", "b|c"), listOf("", "\"q\"")), header = false)
        val json = BlockConverter.blocksToJson(listOf(Block.TableBlock(MutableStateFlow(data))))

        assertTrue("\"blockType\": \"TABLE\"" in json, json)
        assertTrue("\"hasHeader\": false" in json, json)
        assertEquals(listOf("table-headless:a|b|c/|\"q\""), describe(BlockConverter.jsonToBlocks(json)))
    }

    @Test
    fun aStoredTableWithoutTheHeaderKeyHasOne() {
        val json = """[{"blockType":"TABLE","rows":[["a"],["b"]]}]"""

        assertEquals(listOf("table:a/b"), describe(BlockConverter.jsonToBlocks(json)))
    }

    @Test
    fun aStoredCodeBlockWithoutTextIsEmpty() {
        assertEquals(listOf("code:"), describe(BlockConverter.jsonToBlocks("""[{"blockType":"CODE"}]""")))
    }

    @Test
    fun aStoredTableWithoutRowsIsOneEmptyCell() {
        assertEquals(listOf("table:"), describe(BlockConverter.jsonToBlocks("""[{"blockType":"TABLE"}]""")))
    }

    @Test
    fun aMalformedTableIsRefusedLikeAnyMalformedBlock() {
        assertFailsWith<SerializationException> {
            BlockConverter.jsonToBlocks("""[{"blockType":"TABLE","rows":["a"]}]""")
        }
        assertFailsWith<SerializationException> {
            BlockConverter.jsonToBlocks("""[{"blockType":"TABLE","rows":[[null]]}]""")
        }
        assertFailsWith<SerializationException> {
            BlockConverter.jsonToBlocks("""[{"blockType":"TABLE","rows":[[["a"]]]}]""")
        }
        assertNull(BlockConverter.jsonToBlocksOrNull("""[{"blockType":"TABLE","rows":"a"}]"""))
    }

    @Test
    fun aTypeThisVersionDoesNotKnowIsStillRefusedWholeSoNothingIsSilentlyDropped() {
        assertFailsWith<SerializationException> {
            BlockConverter.jsonToBlocks("""[{"blockType":"TEXT","text":"a"},{"blockType":"CALLOUT","text":"b"}]""")
        }
    }

    @Test
    fun emptyCodeAndTablesAreStoredWhereverTheyAre() {
        // The blank paragraph a fresh note starts with is still dropped, but a block the user made is kept.
        val alone = BlockConverter.blocksToJson(listOf(Block.CodeBlock(), Block.TableBlock(), Block.TextBlock()))
        assertEquals(listOf("code:", "table:|/|"), describe(BlockConverter.jsonToBlocks(alone)))

        val between = BlockConverter.blocksToJson(
            listOf(Block.CodeBlock(MutableStateFlow("x")), Block.TableBlock(), Block.TextBlock(MutableStateFlow("t"))),
        )
        assertEquals(listOf("code:x", "table:|/|", "text:t"), describe(BlockConverter.jsonToBlocks(between)))
    }

    // --- Markdown import --------------------------------------------------------------------------

    @Test
    fun fencedCodeKeepsEverySpaceAndIsNotRewritten() {
        assertEquals(
            listOf("code:  a *b* <i>c</i>\n\n\t  d"),
            md("~~~js\n  a *b* <i>c</i>\n\n\t  d\n\n~~~"),
        )
        assertEquals(emptyList(), md("```\n\n```"))
    }

    @Test
    fun aPipeTableIsATableWhateverItsAlignmentAndOuterBars() {
        assertEquals(listOf("table:a|b/1|2"), md("| a | b |\n| :--- | ---: |\n| 1 | 2 |"))
        assertEquals(listOf("table:a|b/1|2"), md("a | b\n:-:|--\n1 | 2"))
        assertEquals(listOf("table:a/1/2"), md("|a|\n|-|\n|1|\n|2|"))
    }

    @Test
    fun aBarInACellIsEscapedAndSoIsABackslash() {
        assertEquals(listOf("table:a|b/x|y|z\\w"), md("| a | b |\n|---|---|\n| x\\|y | z\\w |"))
        assertEquals(listOf("table:a/p\\q"), md("| a |\n|---|\n| p\\\\q |"))
    }

    @Test
    fun aFenceEndsOnlyAtAFenceOfTheSameMarkAtLeastAsLong() {
        assertEquals(listOf("code:```kotlin\nx\n```"), md("````\n```kotlin\nx\n```\n````"))
        assertEquals(listOf("code:a\n~~~\nb"), md("```\na\n~~~\nb\n```"))
        assertEquals(listOf("code:a\n``` not a close\nb"), md("```\na\n``` not a close\nb\n```"))
        assertEquals(listOf("code:open until the end"), md("```\nopen until the end"))
        assertEquals(listOf("code:a", "text:after"), md("```\na\n`````\n\nafter"))
    }

    @Test
    fun rowsAreMadeAsWideAsTheWidest() {
        assertEquals(listOf("table:a|b|/1||/1|2|3"), md("| a | b |\n|---|---|\n| 1 |\n| 1 | 2 | 3 |"))
    }

    @Test
    fun aHeaderOfEmptyCellsIsATableWithoutAHeader() {
        assertEquals(listOf("table-headless:x|y"), md("|  |  |\n|---|---|\n| x | y |"))
        assertEquals(listOf("table-headless:"), md("|\n|---|"))
    }

    @Test
    fun aTableEndsAtABlankLineAParagraphOrAFenceAndInterruptsAParagraph() {
        assertEquals(listOf("text:before", "table:a/1", "text:after"), md("before\n| a |\n|---|\n| 1 |\n\nafter"))
        assertEquals(listOf("table:a/1", "text:after"), md("| a |\n|---|\n| 1 |\nafter"))
        assertEquals(listOf("table:a/1", "code:x"), md("| a |\n|---|\n| 1 |\n```\nx\n```"))
        assertEquals(listOf("table:a", "code:y"), md("| a |\n|---|\n```js|x\ny\n```"))
        assertEquals(listOf("table:a/1", "table:b/2"), md("| a |\n|---|\n| 1 |\n\n| b |\n|---|\n| 2 |"))
    }

    @Test
    fun aLineWithABarThatIsNoTableHeaderStaysText() {
        assertEquals(listOf("text:a | b"), md("a | b"))
        assertEquals(listOf("text:a | b\nnext"), md("a | b\nnext"))
        assertEquals(listOf("text:a | b", "text:next"), md("a | b\n\nnext"))
        assertEquals(listOf("text:a | b\nc | d"), md("a | b\nc | d"))
        // The delimiter must have the header's number of cells, each only dashes and colons.
        assertEquals(listOf("text:a | b\n--- | ---- | -"), md("a | b\n--- | ---- | -"))
        assertEquals(listOf("text:a | b\nx | y"), md("a | b\nx | y"))
        assertEquals(listOf("text:a | b", "SeparatorBlock"), md("a | b\n---"))
        assertEquals(listOf("h:a | b"), md("# a | b"))
        assertEquals(listOf("list:a | b"), md("- a | b"))
        assertEquals(listOf("text:a | b", "code:x"), md("a | b\n```\nx\n```"))
    }

    @Test
    fun aPasteKeepsTablesAndFencesAsTypedText() {
        assertEquals(
            listOf("text:| a | b |\n|---|---|"),
            describe(MarkdownBlockMapper.pasteBlocks("| a | b |\n|---|---|")),
        )
        assertEquals(listOf("text:```\nx\n```"), describe(MarkdownBlockMapper.pasteBlocks("```\nx\n```")))
    }

    @Test
    fun importBlocksBuildCodeAndTables() {
        assertEquals(listOf("code:x"), describe(listOf(ImportBlocks.code("x"))))
        val table = ImportBlocks.table(listOf(listOf("a", "b"), listOf("c")), header = false)
        assertEquals(listOf("table-headless:a|b/c|"), describe(listOf(table)))
    }
}
