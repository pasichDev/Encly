package com.pasich.encly.utils

import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.domain.model.LinkDataBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

/** The plain-text and search renderings of every block type: what is kept, what is dropped, in what order. */
class NotesTextFormatterBlocksTest {
    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    private fun list(type: BlockType, vararg items: ItemListBlock) =
        Block.ListBlock(MutableStateFlow(items.toList()), type)

    private fun link(title: String, url: String) =
        Block.LinkBlock(MutableStateFlow(LinkDataBlock(title = title, url = url)))

    // --- plain text ----------------------------------------------------------------------

    @Test
    fun textHeadingsAndQuotesBecomeParagraphsWithQuotesInGuillemets() {
        val blocks = listOf(
            Block.HBlock(MutableStateFlow("Title"), BlockType.H1),
            text("Body"),
            Block.QuoteBlock(MutableStateFlow("Said")),
        )

        assertEquals("Title\n\nBody\n\n«Said»", NotesTextFormatter.blocksToPlainText(blocks))
    }

    @Test
    fun aLinkShowsItsTitleWithTheAddressOrJustTheAddress() {
        val blocks = listOf(link("Example", "https://example.com"), link("", "https://bare.example"))

        assertEquals(
            "Example (https://example.com)\n\nhttps://bare.example",
            NotesTextFormatter.blocksToPlainText(blocks),
        )
    }

    @Test
    fun aSeparatorIsALineOfDashes() {
        assertEquals(
            "a\n\n---------------------\n\nb",
            NotesTextFormatter.blocksToPlainText(listOf(text("a"), Block.SeparatorBlock(), text("b"))),
        )
    }

    @Test
    fun checklistsShowTheirStateNumberedListsKeepTheirNumbersAndOthersGetHyphens() {
        val checked = list(BlockType.LIST_CHECK, ItemListBlock("done", true), ItemListBlock("open", false))
        val numbered = list(BlockType.LIST_NUMBER, ItemListBlock("first"), ItemListBlock("second"))
        val bullets = list(BlockType.LIST_BULLET, ItemListBlock("x"), ItemListBlock("y"))

        assertEquals(
            "[x] done\n[ ] open\n\n1. first\n2. second\n\n- x\n- y",
            NotesTextFormatter.blocksToPlainText(listOf(checked, numbered, bullets)),
        )
    }

    @Test
    fun anEmptyListAddsNothing() {
        val empty = Block.ListBlock(MutableStateFlow(emptyList()), BlockType.LIST_BULLET)

        assertEquals("a", NotesTextFormatter.blocksToPlainText(listOf(text("a"), empty)))
    }

    @Test
    fun noBlocksIsNoText() {
        assertEquals("", NotesTextFormatter.blocksToPlainText(emptyList()))
    }

    // --- search text ---------------------------------------------------------------------

    @Test
    fun searchSeesOnlyWhatTheUserTypedOnePerLine() {
        val blocks = listOf(
            Block.HBlock(MutableStateFlow("Heading"), BlockType.H2),
            text("Body"),
            Block.QuoteBlock(MutableStateFlow("Quoted")),
            list(BlockType.LIST_CHECK, ItemListBlock("task one", true), ItemListBlock("task two")),
            link("Site", "https://site.example"),
            Block.SeparatorBlock(),
        )

        assertEquals(
            "Heading\nBody\nQuoted\ntask one\ntask two\nSite\nhttps://site.example",
            NotesTextFormatter.blocksToSearchText(blocks),
        )
    }

    @Test
    fun searchSkipsBlankPartsSoNoQueryMatchesNothingInParticular() {
        val blocks =
            listOf(text("  "), list(BlockType.LIST_BULLET, ItemListBlock(""), ItemListBlock("kept")), link("", "u"))

        assertEquals("kept\nu", NotesTextFormatter.blocksToSearchText(blocks))
    }

    @Test
    fun searchTextHasNoMarkersForAQueryLikeXOrDashToMatch() {
        val blocks = listOf(
            list(BlockType.LIST_CHECK, ItemListBlock("alpha", true)),
            Block.SeparatorBlock(),
            Block.QuoteBlock(MutableStateFlow("beta")),
        )

        val search = NotesTextFormatter.blocksToSearchText(blocks)

        assertEquals(false, search.contains("[x]"))
        assertEquals(false, search.contains("-"))
        assertEquals(false, search.contains("«"))
    }
}
