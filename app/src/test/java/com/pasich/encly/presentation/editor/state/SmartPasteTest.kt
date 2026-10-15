package com.pasich.encly.presentation.editor.state

import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smart Paste: what pasted text becomes, as explicit fixtures. Rules under test: a blank line (any
 * number of them) starts the next paragraph and a single line break stays inside its paragraph;
 * whitespace-only lines at the ends are not content and are dropped, none in between is; Markdown
 * marks become their blocks; whatever the editor has no block for stays readable text.
 */
class SmartPasteTest {
    private val editor = BlockEditorState { 0L }

    // --- what text becomes ---------------------------------------------------------------------

    @Test
    fun aBlankLineStartsAParagraphAndASingleLineBreakStaysInsideIt() {
        assertEquals(
            listOf("P:one\ntwo", "P:three"),
            shape(pasted("one", "two", "", "three")),
        )
    }

    @Test
    fun severalBlankLinesAreOneParagraphBreak() {
        // An empty paragraph is never stored, so extra blank lines could not survive anyway.
        assertEquals(listOf("P:one", "P:two"), shape(pasted("one", "", "", "  ", "two")))
    }

    @Test
    fun whitespaceOnlyLinesAtEitherEndAreDroppedButNothingElse() {
        assertEquals(listOf("P:text"), shape(pasted("", "  ", "text", "\t", "")))
        // Leading spaces of a whole paragraph are layout, not content, when it becomes a block.
        assertEquals(listOf("P:indented"), shape(pasted("  indented")))
        assertTrue(pasted("", "   ", "\t").isEmpty())
    }

    @Test
    fun headingsLevelsOneToFourAndDeeperOnesAsFour() {
        assertEquals(
            listOf("H1:A", "H2:B", "H3:C", "H4:D", "H4:E"),
            shape(pasted("# A", "## B", "### C", "#### D", "##### E")),
        )
    }

    @Test
    fun adjacentListsOfDifferentKindsStaySeparateLists() {
        assertEquals(
            listOf("LIST_BULLET:a|b", "LIST_NUMBER:c|d", "LIST_BULLET:e"),
            shape(pasted("- a", "* b", "1. c", "2) d", "+ e")),
        )
    }

    @Test
    fun checklistsInEveryMarkTheEditorKnows() {
        assertEquals(
            listOf("LIST_CHECK:[ ]a|[x]b|[ ]c|[x]d|[ ]e"),
            shape(pasted("- [ ] a", "- [x] b", "[] c", "[x] d", "* [ ] e")),
        )
    }

    @Test
    fun theEditorsOwnBulletGlyphIsABullet() {
        assertEquals(listOf("LIST_BULLET:a|b"), shape(pasted("• a", "• b")))
    }

    @Test
    fun aListContinuesAcrossABlankLineAndAParagraphEndsIt() {
        assertEquals(
            listOf("P:intro", "LIST_BULLET:a|b", "P:outro"),
            shape(pasted("intro", "", "- a", "", "- b", "outro")),
        )
    }

    @Test
    fun quotesRulesAndAddresses() {
        assertEquals(
            listOf("QUOTE:one\ntwo", "---", "---", "---", "LINK:https://example.com/a", "P:see https://example.com"),
            shape(pasted("> one", "> two", "---", "***", "___", " https://example.com/a ", "see https://example.com")),
        )
    }

    @Test
    fun nothingTheEditorHasNoBlockForIsRewrittenOrDropped() {
        val kept = listOf(
            "use <div class=\"x\">here</div> & <b>there</b>",
            "[[Note|alias]] and ![[image.png]]",
            "| a | b |",
            "2024. Report",
            "> [!note] Callout title",
        )
        assertEquals(
            listOf(
                "P:use <div class=\"x\">here</div> & <b>there</b>\n[[Note|alias]] and ![[image.png]]\n| a | b |",
                "P:2024. Report",
                "QUOTE:[!note] Callout title",
            ),
            shape(pasted(kept[0], kept[1], kept[2], "", kept[3], "", kept[4])),
        )
    }

    @Test
    fun fencedCodeKeepsItsFencesAndItsBlankLines() {
        assertEquals(
            listOf("P:```kotlin\nval a = 1\n\nval b = 2\n```"),
            shape(pasted("```kotlin", "val a = 1", "", "val b = 2", "```")),
        )
    }

    @Test
    fun trailingSpacesOfALineAreTheOnlyWhitespaceThatIsTrimmedInsideParagraphs() {
        assertEquals(listOf("P:a\nb"), shape(pasted("a   ", "b  ")))
    }

    // --- pasting into the editor ----------------------------------------------------------------

    @Test
    fun aMultiParagraphPasteIntoAnEmptyParagraphIsOneUndoStep() {
        val block = Block.TextBlock()
        editor.load(listOf(block))

        editor.breakLine(block.id, paste("", 0, "# Trip\n\nPack light\nand early\n\n- milk\n- eggs"))

        assertEquals(listOf("H1:Trip", "P:Pack light\nand early", "LIST_BULLET:milk|eggs"), shape(editor.blocks))
        editor.undo()
        assertEquals(listOf("P:"), shape(editor.blocks))
        editor.redo()
        assertEquals(3, editor.blocks.size)
    }

    @Test
    fun aSingleParagraphGoesIntoTheBlockAtTheCursorLineBreaksAndAll() {
        val block = Block.TextBlock(MutableStateFlow("Start end"))
        editor.load(listOf(block))

        val kept = editor.breakLine(block.id, paste("Start end", 6, "one\ntwo"))

        assertEquals("Start one\ntwoend", kept)
        assertEquals(listOf("P:Start one\ntwoend"), shape(editor.blocks))
        assertFocus(0, caret = "Start one\ntwo".length)
        editor.undo()
        assertEquals(listOf("P:Start end"), shape(editor.blocks))
    }

    @Test
    fun structureInTheMiddleOfTextSplitsItAndTheRestFollows() {
        val block = Block.TextBlock(MutableStateFlow("abcd"))
        editor.load(listOf(block))

        val kept = editor.breakLine(block.id, paste("abcd", 2, "x\n- l1\n- l2"))

        assertEquals("abx", kept)
        assertEquals(listOf("P:abx", "LIST_BULLET:l1|l2", "P:cd"), shape(editor.blocks))
        assertFocus(2, caret = 0)
        editor.undo()
        assertEquals(listOf("P:abcd"), shape(editor.blocks))
    }

    @Test
    fun aHeadingMarkFirstInAPasteAfterTextIsPlainTextAndTheTextIsNotSplit() {
        val block = Block.TextBlock(MutableStateFlow("Intro"))
        editor.load(listOf(block))

        editor.breakLine(block.id, paste("Intro", 5, "# Head\ntext"))

        assertEquals(listOf("P:Intro# Head\ntext"), shape(editor.blocks))
    }

    @Test
    fun theFirstLineOfACodeOrLogPasteIsNotReadAsAMarkMidParagraph() {
        val block = Block.TextBlock(MutableStateFlow("abc"))
        editor.load(listOf(block))

        editor.breakLine(block.id, paste("abc", 3, "# install deps\nnpm i"))

        assertEquals(listOf("P:abc# install deps\nnpm i"), shape(editor.blocks))
    }

    @Test
    fun linesAfterThePlainFirstLineStillConvert() {
        val block = Block.TextBlock(MutableStateFlow("abc"))
        editor.load(listOf(block))

        editor.breakLine(block.id, paste("abc", 3, "- x\n# H"))

        assertEquals(listOf("P:abc- x", "H1:H"), shape(editor.blocks))
    }

    @Test
    fun aSingleLineWithATrailingLineBreakIsPlainTextNextToText() {
        val block = Block.TextBlock(MutableStateFlow("abc"))
        editor.load(listOf(block))
        editor.breakLine(block.id, paste("abc", 3, "see https://x.com\n"))
        assertEquals(listOf("P:abcsee https://x.com"), shape(editor.blocks))

        val other = Block.TextBlock(MutableStateFlow("abc"))
        editor.load(listOf(other))
        editor.breakLine(other.id, paste("abc", 3, "https://x.com\n"))
        assertEquals(listOf("P:abchttps://x.com"), shape(editor.blocks))

        // Into an empty paragraph a lone address is still a link.
        val empty = Block.TextBlock()
        editor.load(listOf(empty))
        editor.breakLine(empty.id, paste("", 0, "https://x.com\n"))
        assertEquals("LINK:https://x.com", shape(editor.blocks).first())
    }

    @Test
    fun theTextAfterTheCursorKeepsTheKindOfTheHeadingOrQuote() {
        val heading = Block.HBlock(MutableStateFlow("Title"), BlockType.H2)
        editor.load(listOf(heading))
        editor.breakLine(heading.id, paste("Title", 2, "x\n\n- l"))
        assertEquals(listOf("H2:Tix", "LIST_BULLET:l", "H2:tle"), shape(editor.blocks))

        val joined = Block.HBlock(MutableStateFlow("Title"), BlockType.H3)
        editor.load(listOf(joined))
        editor.breakLine(joined.id, paste("Title", 2, "x\n\ny"))
        assertEquals(listOf("H3:Tix", "H3:ytle"), shape(editor.blocks))

        val quote = Block.QuoteBlock(MutableStateFlow("abcd"))
        editor.load(listOf(quote))
        editor.breakLine(quote.id, paste("abcd", 2, "x\n- l"))
        assertEquals(listOf("QUOTE:abx", "LIST_BULLET:l", "QUOTE:cd"), shape(editor.blocks))
    }

    @Test
    fun anEmptyChecklistOrBulletTemplateIsPastedWithItsEmptyItems() {
        assertEquals(listOf("LIST_CHECK:[ ]|[ ]"), shape(pasted("- [ ] ", "- [ ] ")))
        assertEquals(listOf("LIST_BULLET:a||b"), shape(pasted("- a", "- ", "- b")))
        assertEquals(listOf("LIST_NUMBER:|"), shape(pasted("1. ", "2. ")))
        assertEquals(listOf("LIST_BULLET:|"), shape(pasted("• ", "• ")))

        val block = Block.TextBlock()
        editor.load(listOf(block))
        editor.breakLine(block.id, paste("", 0, "- [ ] \n- [ ] \n"))
        assertEquals("LIST_CHECK:[ ]|[ ]", shape(editor.blocks).first())
    }

    @Test
    fun textTheMapperLeavesNothingOfIsPastedAsTyped() {
        val block = Block.TextBlock()
        editor.load(listOf(block))

        val kept = editor.breakLine(block.id, paste("", 0, "> \n> "))

        assertEquals("> \n> ", kept)
        assertEquals(listOf("P:> \n> "), shape(editor.blocks))
    }

    @Test
    fun pastingBlankLinesOnlyChangesNothing() {
        val block = Block.TextBlock(MutableStateFlow("abcd"))
        editor.load(listOf(block))

        val kept = editor.breakLine(block.id, paste("abcd", 2, "\n \n"))

        assertEquals("abcd", kept)
        assertEquals(listOf("P:abcd"), shape(editor.blocks))
    }

    @Test
    fun aParagraphPastedIntoAHeadingKeepsOneLine() {
        val heading = Block.HBlock(MutableStateFlow("Title"), BlockType.H2)
        editor.load(listOf(heading))

        val kept = editor.breakLine(heading.id, paste("Title", 5, " a\nb"))

        assertEquals("Title a b", kept)
        assertEquals(listOf("H2:Title a b"), shape(editor.blocks))
    }

    // --- helpers -------------------------------------------------------------------------------

    private fun pasted(vararg lines: String) = pastedBlocks(lines.toList())

    private fun paste(text: String, cursor: Int, pasted: String) =
        LineBreak(text.take(cursor), pasted.split('\n'), text.drop(cursor))

    private fun assertFocus(index: Int, caret: Int?) {
        var request: FocusRequest? = null
        runTest { request = editor.selection.requests.first() }
        assertEquals(editor.blocks[index].id, request?.blockId)
        assertEquals(caret, request?.caret)
    }

    private fun shape(blocks: List<Block>): List<String> = blocks.map { block ->
        when (block) {
            is Block.TextBlock -> "P:${block.text.value}"

            is Block.HBlock -> "${block.blockType}:${block.text.value}"

            is Block.QuoteBlock -> "QUOTE:${block.text.value}"

            is Block.LinkBlock -> "LINK:${block.block.value.url}"

            is Block.SeparatorBlock -> "---"

            is Block.ListBlock -> block.blockType.name + ":" + block.items.value.joinToString("|") {
                (if (block.blockType == BlockType.LIST_CHECK) if (it.isCheck) "[x]" else "[ ]" else "") + it.value
            }
        }
    }
}
