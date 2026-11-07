package com.pasich.encly.data.export

import com.pasich.encly.data.model.Note
import com.pasich.encly.editor.codec.BlockConverter
import com.pasich.encly.editor.codec.MarkdownBlockMapper
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.TableData
import com.pasich.encly.utils.NotesTextFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

/** Code and tables in the Markdown export, the document for paper or PDF, and the plain-text renderings. */
class TablesAndCodeExportTest {
    private fun code(value: String) = Block.CodeBlock(MutableStateFlow(value))

    private fun table(vararg rows: List<String>, header: Boolean = true) =
        Block.TableBlock(MutableStateFlow(TableData.of(rows.toList(), header)))

    private fun render(vararg blocks: Block) = MarkdownBlocks.render(blocks.toList())

    private fun grid(block: Block) = (block as Block.TableBlock).table.value.rows.map { row ->
        row.cells.map { it.value }
    }

    // --- Markdown ----------------------------------------------------------------------------------------

    @Test
    fun codeIsAFencedBlockThatKeepsEverySpaceAndLine() {
        assertEquals("```\n  if (a < b) {\n\tx()\n}\n```", render(code("  if (a < b) {\n\tx()\n}")))
    }

    @Test
    fun theFenceIsLongerThanAnyRunOfBackticksInsideTheCode() {
        assertEquals("````\nuse ``` here\n````", render(code("use ``` here")))
        assertEquals("```\nuse `x` here\n```", render(code("use `x` here")))
        assertEquals("```\na\n```", render(code("a\n\n")))
    }

    @Test
    fun aTableIsAGfmPipeTableWithEscapedBarsAndOneLineCells() {
        val block = table(listOf("Name", "a|b"), listOf("x *y*", "line\nbreak"))

        assertEquals(
            "| Name | a\\|b |\n| --- | --- |\n| x \\*y\\* | line break |",
            render(block),
        )
    }

    @Test
    fun aTableWithoutAHeaderIsWrittenWithAnEmptyHeaderRow() {
        assertEquals(
            "|  |  |\n| --- | --- |\n| a | b |",
            render(table(listOf("a", "b"), header = false)),
        )
    }

    @Test
    fun whatTheExportWritesTheImportReadsBackExactly() {
        val blocks = listOf(
            table(listOf("h1", "h|2"), listOf("c\\d", "e"), listOf("", "f")),
            code("```kotlin\nval a = 1 | 2\n```\n\n  indented"),
            table(listOf("only", "body"), listOf("x", "y"), header = false),
        )

        val back = MarkdownBlockMapper.toBlocks(MarkdownBlocks.render(blocks))

        assertEquals(3, back.size)
        assertEquals(grid(blocks[0]), grid(back[0]))
        assertEquals("```kotlin\nval a = 1 | 2\n```\n\n  indented", (back[1] as Block.CodeBlock).text.value)
        assertEquals(grid(blocks[2]), grid(back[2]))
        assertEquals(false, (back[2] as Block.TableBlock).table.value.header)
    }

    // --- the document for paper and PDF ------------------------------------------------------------------

    @Test
    fun codeAndTablesAreDrawnAsParagraphsOfTheirText() {
        val note = Note.new(
            title = "t",
            value = BlockConverter.blocksToJson(listOf(code("a\n  b"), table(listOf("x", "y"), listOf("z", "")))),
        )

        val document = NoteDocument.of(note, tagName = null, untitled = "Untitled", unreadable = "?")

        assertEquals(
            listOf(DocumentBlock.Paragraph("a\n  b"), DocumentBlock.Paragraph("x | y\nz | ")),
            document.blocks,
        )
    }

    // --- plain text and search ---------------------------------------------------------------------------

    @Test
    fun plainTextAndSearchReadCodeAndEveryCell() {
        val blocks = listOf(code("run()"), table(listOf("a", "b"), listOf("c", "d")))

        assertEquals("run()\na | b\nc | d", NotesTextFormatter.blocksToPlainText(blocks).trimEnd())
        assertEquals("run()\na\nb\nc\nd", NotesTextFormatter.blocksToSearchText(blocks))
    }
}
