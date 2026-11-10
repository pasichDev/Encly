package com.pasich.encly.data.export

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.LinkDataBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MarkdownBlocksTest {

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))
    private fun heading(type: BlockType, value: String) = Block.HBlock(MutableStateFlow(value), type)
    private fun quote(value: String) = Block.QuoteBlock(MutableStateFlow(value))
    private fun link(title: String, url: String) =
        Block.LinkBlock(MutableStateFlow(LinkDataBlock(title = title, url = url)))
    private fun list(type: BlockType, vararg items: ItemListBlock) =
        Block.ListBlock(MutableStateFlow(items.toList()), type)
    private fun render(vararg blocks: Block) = MarkdownBlocks.render(blocks.toList())

    // --- one test per block type ------------------------------------------------------------------

    @Test
    fun aTextBlockIsAParagraph() {
        assertEquals("Hello world", render(text("Hello world")))
    }

    @Test
    fun headingsKeepTheirLevel() {
        assertEquals("# One", render(heading(BlockType.H1, "One")))
        assertEquals("## Two", render(heading(BlockType.H2, "Two")))
        assertEquals("### Three", render(heading(BlockType.H3, "Three")))
        assertEquals("#### Four", render(heading(BlockType.H4, "Four")))
    }

    @Test
    fun aQuotePrefixesEveryLine() {
        assertEquals("> first\\\n> second\n>\n> after a gap", render(quote("first\nsecond\n\nafter a gap")))
    }

    @Test
    fun aChecklistUsesTaskListBoxes() {
        val block = list(BlockType.LIST_CHECK, ItemListBlock("done", isCheck = true), ItemListBlock("todo"))
        assertEquals("- [x] done\n- [ ] todo", render(block))
    }

    @Test
    fun aBulletedListUsesDashes() {
        assertEquals("- a\n- b", render(list(BlockType.LIST_BULLET, ItemListBlock("a"), ItemListBlock("b"))))
    }

    @Test
    fun aNumberedListCountsFromOne() {
        val block = list(BlockType.LIST_NUMBER, ItemListBlock("a"), ItemListBlock("b"), ItemListBlock("c"))
        assertEquals("1. a\n2. b\n3. c", render(block))
    }

    @Test
    fun aLinkIsATitledLink() {
        assertEquals("[Encly](https://example.com/a)", render(link("Encly", "https://example.com/a")))
    }

    @Test
    fun aLinkWithoutATitleShowsItsAddress() {
        assertEquals("[https://example.com](https://example.com)", render(link("", "https://example.com")))
    }

    @Test
    fun aSeparatorIsARule() {
        assertEquals("---", render(Block.SeparatorBlock()))
    }

    // --- structure -----------------------------------------------------------------------------------

    @Test
    fun blocksAreSeparatedByABlankLineSoARuleNeverMakesAHeading() {
        assertEquals("above\n\n---\n\nbelow", render(text("above"), Block.SeparatorBlock(), text("below")))
    }

    @Test
    fun blocksWithoutContentAreLeftOut() {
        assertEquals("kept", render(text(""), text("kept"), list(BlockType.LIST_BULLET, ItemListBlock(" "))))
        assertEquals("", render())
    }

    @Test
    fun blankItemsOfAListAreSkipped() {
        val block = list(BlockType.LIST_NUMBER, ItemListBlock("a"), ItemListBlock(""), ItemListBlock("b"))
        assertEquals("1. a\n2. b", render(block))
    }

    @Test
    fun aLineBreakInsideAParagraphIsAHardBreak() {
        assertEquals("one\\\ntwo", render(text("one\ntwo")))
        assertEquals("one\\\ntwo\n\nthree", render(text("one\ntwo\n\n\n\nthree")))
    }

    @Test
    fun trailingSpacesAndWindowsLineEndingsAreNormalised() {
        assertEquals("one\\\ntwo", render(text("one  \r\ntwo   ")))
    }

    // --- escaping --------------------------------------------------------------------------------------

    @Test
    fun inlineMarksAreEscaped() {
        assertEquals("""a \*b\* \`c\` \[d\] \<e> \~f\~ g\\h i\|j""", render(text("a *b* `c` [d] <e> ~f~ g\\h i|j")))
    }

    @Test
    fun anUnderscoreInsideAWordStaysButAnEmphasisOneIsEscaped() {
        assertEquals("snake_case and \\_emphasis\\_", render(text("snake_case and _emphasis_")))
    }

    @Test
    fun anEntityIsNotDecoded() {
        assertEquals("fish &amp;amp; chips and a & b", render(text("fish &amp; chips and a & b")))
    }

    @Test
    fun whatWouldStartABlockIsEscapedAtTheStartOfALine() {
        assertEquals("\\# not a heading", render(text("# not a heading")))
        assertEquals("\\> not a quote", render(text("> not a quote")))
        assertEquals("\\- not a bullet", render(text("- not a bullet")))
        assertEquals("\\+ not a bullet", render(text("+ not a bullet")))
        assertEquals("1\\. not a list", render(text("1. not a list")))
        assertEquals("2\\) not a list", render(text("2) not a list")))
        assertEquals("\\---", render(text("---")))
        assertEquals("\\===", render(text("===")))
    }

    @Test
    fun aLineOfDashesCannotTurnTheLineAboveIntoAHeading() {
        // "foo" then "--": a setext underline unless the dashes are escaped.
        assertEquals("foo\\\n\\--", render(text("foo\n--")))
    }

    @Test
    fun hashesInTheMiddleOfALineAreKept() {
        assertEquals("issue #12 and C#", render(text("issue #12 and C#")))
    }

    @Test
    fun anIndentOfFourSpacesIsNotACodeBlock() {
        val line = render(text("    indented"))
        assertFalse(line.startsWith("    "))
        assertEquals("indented", line.trim { it == ' ' || it == '\u00A0' })
    }

    @Test
    fun aListItemIsEscapedLikeALineStart() {
        assertEquals("- \\# heading?", render(list(BlockType.LIST_BULLET, ItemListBlock("# heading?"))))
        assertEquals("1. 2\\. nested?", render(list(BlockType.LIST_NUMBER, ItemListBlock("2. nested?"))))
        assertEquals("- [ ] \\[x\\] looks done", render(list(BlockType.LIST_CHECK, ItemListBlock("[x] looks done"))))
    }

    @Test
    fun aListItemWithALineBreakIsIndentedUnderItsMarker() {
        assertEquals(
            "1. a\\\n   b\n2. c",
            render(list(BlockType.LIST_NUMBER, ItemListBlock("a\nb"), ItemListBlock("c"))),
        )
    }

    @Test
    fun aHeadingIsOneLineAndAClosingHashIsEscaped() {
        assertEquals("## one two", render(heading(BlockType.H2, "one\n  two")))
        assertEquals("# C\\#", render(heading(BlockType.H1, "C#")))
        assertEquals("# a \\*b\\*", render(heading(BlockType.H1, "a *b*")))
    }

    @Test
    fun controlCharactersAreDropped() {
        assertEquals("ab", render(text("a\u0000\u001bb")))
    }

    // --- links ------------------------------------------------------------------------------------------

    @Test
    fun aLinkLabelIsEscapedAndTheAddressKeepsItsParentheses() {
        assertEquals(
            "[a \\[b\\] \\*c\\*](https://example.com/x%28y%29%20z)",
            render(link("a [b] *c*", "https://example.com/x(y) z")),
        )
    }

    @Test
    fun onlyWebMailAndPhoneAddressesBecomeLinks() {
        assertEquals("[m](mailto:me@example.com)", render(link("m", "mailto:me@example.com")))
        assertEquals("[t](tel:+123)", render(link("t", "tel:+123")))
        assertEquals("[h](HTTP://example.com)", render(link("h", "HTTP://example.com")))
    }

    @Test
    fun anAddressWithAnotherSchemeIsWrittenAsCodeNotAsALink() {
        assertEquals("`javascript:alert(1)`", render(link("click", "javascript:alert(1)")))
        assertEquals("`data:text/html,x`", render(link("", "data:text/html,x")))
    }

    @Test
    fun aSchemeSplitByAControlCharacterIsNotALink() {
        val out = render(link("x", "java\tscript:alert(1)"))
        assertFalse(out.contains("]("))
    }

    @Test
    fun anAddressCannotBreakOutOfItsParentheses() {
        val out = render(link("x", "https://a.com/)[evil](javascript:alert(1)"))
        assertEquals("[x](https://a.com/%29[evil]%28javascript:alert%281%29)", out)
    }

    @Test
    fun aPreviewImageIsNeverWritten() {
        val block = Block.LinkBlock(
            MutableStateFlow(
                LinkDataBlock(title = "t", imageUrl = "https://tracker.example/p.png", url = "https://a.com"),
            ),
        )
        assertFalse(render(block).contains("tracker.example"))
    }
}
