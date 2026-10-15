package com.pasich.encly.presentation.editor.state

import com.pasich.encly.core.serialization.BlockConverter
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

/** Find in this note: where it looks, how it moves, and that it never touches the note. */
class NoteFindTest {
    private val paragraph = Block.TextBlock(MutableStateFlow("The cat sat. The CAT slept."))
    private val heading = Block.HBlock(MutableStateFlow("Cats"), BlockType.H2)
    private val quote = Block.QuoteBlock(MutableStateFlow("no match here"))
    private val list = Block.ListBlock(
        MutableStateFlow(listOf(ItemListBlock("feed the cat"), ItemListBlock("nothing"), ItemListBlock("cat toys"))),
        BlockType.LIST_BULLET,
    )
    private val link = Block.LinkBlock(MutableStateFlow(LinkDataBlock(title = "cat", url = "https://cat.example")))
    private val blocks = listOf(paragraph, heading, quote, list, link, Block.SeparatorBlock())

    @Test
    fun matchesAcrossParagraphsHeadingsAndListItemsInReadingOrderIgnoringCase() {
        val found = findMatches(blocks, "cat")

        assertEquals(
            listOf(
                Triple(paragraph.id, null, 4..7),
                Triple(paragraph.id, null, 17..20),
                Triple(heading.id, null, 0..3),
                Triple(list.id, list.items.value[0].id, 9..12),
                Triple(list.id, list.items.value[2].id, 0..3),
            ),
            found.map { Triple(it.blockId, it.itemId, it.start..it.end) },
        )
    }

    @Test
    fun matchesDoNotOverlapAndABlankQueryFindsNothing() {
        val block = Block.TextBlock(MutableStateFlow("aaaa"))

        assertEquals(2, findMatches(listOf(block), "aa").size)
        assertTrue(findMatches(listOf(block), "").isEmpty())
        assertTrue(findMatches(listOf(block), "  ").isEmpty())
    }

    @Test
    fun unicodeAndSurrogatePairsKeepTheirOffsets() {
        val block = Block.TextBlock(MutableStateFlow("😀 İstanbul ÄÖ"))

        val found = findMatches(listOf(block), "äö").single()

        assertEquals("ÄÖ", block.text.value.substring(found.start, found.end))
    }

    @Test
    fun nextAndPreviousWrapRoundAndTheFirstMatchIsCurrent() {
        val find = NoteFind().apply { open() }

        find.search("cat", blocks)
        assertEquals(0, find.state.value.current)
        find.previous()
        assertEquals(4, find.state.value.current)
        find.next()
        assertEquals(0, find.state.value.current)
        find.next()
        assertEquals(1, find.state.value.current)
    }

    @Test
    fun nothingFoundMeansNoCurrentMatchAndStepsAreHarmless() {
        val find = NoteFind().apply { open() }

        find.search("zebra", blocks)
        find.next()
        find.previous()

        assertEquals(-1, find.state.value.current)
        assertNull(find.state.value.currentMatch)
    }

    @Test
    fun refreshFollowsTheTextAndKeepsTheCurrentPlaceWhereItCan() {
        val find = NoteFind().apply { open() }
        find.search("cat", blocks)
        find.next()
        find.next()

        paragraph.text.value = "The cat sat."
        find.refresh(blocks)

        assertEquals(4, find.state.value.matches.size)
        assertEquals(2, find.state.value.current)
        list.items.value = emptyList()
        find.refresh(blocks)
        assertEquals(2, find.state.value.matches.size)
        assertEquals(1, find.state.value.current)
    }

    @Test
    fun closingForgetsTheQueryAndEveryMatch() {
        val find = NoteFind().apply { open() }
        find.search("cat", blocks)

        find.close()

        assertEquals(FindState(), find.state.value)
        assertFalse(find.isOpen)
    }

    @Test
    fun matchesAreGroupedByFieldForDrawing() {
        val find = NoteFind().apply { open() }
        find.search("cat", blocks)

        assertEquals(2, find.state.value.byField.getValue(paragraph.id).size)
        assertEquals(1, find.state.value.byField.getValue(fieldKey(list.id, list.items.value[0].id)).size)
    }

    @Test
    fun searchingNeverChangesTheNote() {
        val before = BlockConverter.blocksToJson(blocks)
        val editor = BlockEditorState { 0L }
        editor.load(blocks)

        editor.find.open()
        editor.find.search("cat", editor.blocks)
        editor.find.next()
        editor.find.refresh(editor.blocks)
        editor.find.close()

        assertEquals(before, BlockConverter.blocksToJson(editor.blocks))
        assertFalse(editor.canUndo.value)
    }

    @Test
    fun loadingANoteClosesFind() {
        val editor = BlockEditorState { 0L }
        editor.find.open()
        editor.find.search("x", editor.blocks)

        editor.load(emptyList())

        assertEquals(FindState(), editor.find.state.value)
    }
}
