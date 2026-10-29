package com.pasich.encly.editor.codec

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownBlockMapperTest {
    /** Blocks as short strings for exact assertions. */
    private fun describe(blocks: List<Block>): List<String> = blocks.map { block ->
        when (block) {
            is Block.TextBlock -> "text:${block.text.value}"

            is Block.HBlock -> "${block.blockType.name.lowercase()}:${block.text.value}"

            is Block.QuoteBlock -> "quote:${block.text.value}"

            is Block.SeparatorBlock -> "separator"

            is Block.LinkBlock -> "link"

            is Block.ListBlock -> {
                val kind = when (block.blockType) {
                    BlockType.LIST_CHECK -> "check"
                    BlockType.LIST_NUMBER -> "number"
                    else -> "bullet"
                }
                "$kind:" + block.items.value.joinToString("|") { (if (it.isCheck) "[x] " else "[ ] ") + it.value }
            }
        }
    }

    private fun blocks(markdown: String) = describe(MarkdownBlockMapper.toBlocks(markdown))

    @Test
    fun headingsAreH1ToH4AndDeeperOnesH4() {
        assertEquals(
            listOf("h1:A", "h2:B", "h3:C", "h4:D", "h4:E", "h4:F"),
            blocks("# A\n## B\n### C\n#### D\n##### E\n###### F"),
        )
    }

    @Test
    fun listsAreFlatAndKeepTheirKind() {
        assertEquals(listOf("check:[x] a|[ ] b|[x] c"), blocks("- [x] a\n* [ ] b\n+ [X] c"))
        assertEquals(listOf("bullet:[ ] a|[ ] b|[ ] c"), blocks("- a\n* b\n+ c"))
        assertEquals(listOf("number:[ ] one|[ ] two"), blocks("1. one\n2) two"))
        assertEquals(listOf("bullet:[ ] a|[ ] nested|[ ] b"), blocks("- a\n    - nested\n- b"))
    }

    @Test
    fun aLooseListStaysOneBlockAndAKindChangeStartsANewOne() {
        assertEquals(listOf("bullet:[ ] a|[ ] b", "number:[ ] one"), blocks("- a\n\n- b\n1. one"))
    }

    @Test
    fun quotesRulesAndParagraphs() {
        assertEquals(
            listOf("text:first\nstill first", "quote:q1\nq2", "separator", "separator", "text:last"),
            blocks("first\nstill first\n\n> q1\n> q2\n\n---\n***\n\nlast"),
        )
    }

    @Test
    fun anIndentedLineContinuesTheListItem() {
        assertEquals(listOf("bullet:[ ] a b|[ ] c"), blocks("- a\n  b\n- c"))
    }

    @Test
    fun codeIsKeptAsTextWithoutItsFences() {
        assertEquals(listOf("text:val x = <b>1</b>\nprint(x)"), blocks("```kotlin\nval x = <b>1</b>\nprint(x)\n```"))
    }

    @Test
    fun htmlIsStrippedOutsideCodeSpansAndLinksSurvive() {
        assertEquals(
            listOf("text:bold and `<b>code</b>` <https://x.org> a & b".replace("<https://x.org>", "https://x.org")),
            blocks("<b>bold</b> and `<b>code</b>` <https://x.org> a &amp; b"),
        )
    }

    @Test
    fun noTextIsDropped() {
        assertEquals(
            listOf("text:#hashtag no space", "text:| a | b |\n|---|---|"),
            blocks("#hashtag no space\n\n| a | b |\n|---|---|"),
        )
        assertEquals(emptyList<String>(), blocks("\n\n  \n"))
    }

    // --- paste mode: the same line rules, nothing rewritten --------------------------------------------

    private fun pasted(markdown: String) = describe(MarkdownBlockMapper.pasteBlocks(markdown))

    @Test
    fun aPasteReadsTheSameMarksAsAnImport() {
        val markdown = "# A\n\nintro\nstill intro\n\n- [x] a\n- b\n1. c\n\n> q1\n> q2\n\n---"
        assertEquals(blocks(markdown), pasted(markdown))
    }

    @Test
    fun aPasteKeepsHtmlWikiLinksCalloutMarkersAndFenceLines() {
        assertEquals(
            listOf("text:use <div>here</div> &amp; [[Note|alias]]"),
            pasted("use <div>here</div> &amp; [[Note|alias]]"),
        )
        assertEquals(listOf("quote:[!note] Title\nbody"), pasted("> [!note] Title\n> body"))
        assertEquals(listOf("text:```kotlin\nval x = <b>1</b>\n```"), pasted("```kotlin\nval x = <b>1</b>\n```"))
    }

    @Test
    fun aNumberedListNeedsAtMostThreeDigitsInAPasteAndInAnImport() {
        assertEquals(listOf("text:2024. Report"), pasted("2024. Report"))
        assertEquals(listOf("number:[ ] one"), pasted("12. one"))
        // Encly numbers items itself, so an import cannot keep a start number: a long number is text.
        assertEquals(listOf("text:2024. Report"), blocks("2024. Report"))
        assertEquals(listOf("number:[ ] one|[ ] two"), blocks("1. one\n2) two"))
    }

    @Test
    fun aPasteCanLetTheCallerClaimALineAsABlockOfItsOwn() {
        val claimed = MarkdownBlockMapper.pasteBlocks("before\nhttps://x.org\nafter") { line ->
            ImportBlocks.quote(line).takeIf { line.startsWith("https://") }
        }
        assertEquals(listOf("text:before", "quote:https://x.org", "text:after"), describe(claimed))
    }

    @Test
    fun angleBracketTextThatIsNotHtmlIsKept() {
        assertEquals(
            listOf("text:Map<String, Int> and Dear <name>, <TODO> a<b && c>d"),
            blocks("Map<String, Int> and Dear <name>, <TODO> a<b && c>d"),
        )
    }

    @Test
    fun realInlineTagsAreRemovedAndTheirTextStays() {
        assertEquals(
            listOf("text:x l\ny z"),
            blocks("<b>x</b> <A HREF=\"u\">l</A><br>y <span class='c'>z</span> <img src=x/>"),
        )
    }

    @Test
    fun aLongRunOfBracketsDoesNotHangTheWikiLinkMatch() {
        val started = System.nanoTime()
        MarkdownBlockMapper.toBlocks("[".repeat(200_000))
        MarkdownBlockMapper.toBlocks("[[a|".repeat(50_000))
        assertTrue(System.nanoTime() - started < 1_000_000_000L, "took ${(System.nanoTime() - started) / 1_000_000} ms")
    }

    @Test
    fun aWikiLinkWithBracketsAroundItStillResolves() {
        assertEquals(listOf("text:see alias and Name"), blocks("see [[Note|alias]] and [[Name]]"))
    }

    @Test
    fun anImportDropsABlankItemButAPasteKeepsIt() {
        assertEquals(listOf("bullet:[ ] a|[ ] b"), blocks("- a\n- \n- b"))
        assertEquals(listOf("bullet:[ ] a|[ ] |[ ] b"), pasted("- a\n- \n- b"))
    }
}
