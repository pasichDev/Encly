package com.pasich.encly.data.export

import com.pasich.encly.data.model.Note
import com.pasich.encly.editor.codec.BlockConverter
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.LinkDataBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NoteDocumentTest {
    private fun text(value: String) = Block.TextBlock(text = MutableStateFlow(value))

    private fun heading(type: BlockType, value: String) = Block.HBlock(MutableStateFlow(value), type)

    @Test
    fun everyKindOfBlockBecomesItsDocumentBlock() {
        val blocks = listOf(
            heading(BlockType.H1, "One"),
            heading(BlockType.H4, "Four"),
            text("Plain"),
            Block.QuoteBlock(MutableStateFlow("Said")),
            Block.LinkBlock(MutableStateFlow(LinkDataBlock(title = " Site ", url = " https://example.org "))),
            Block.SeparatorBlock(),
            Block.ListBlock(MutableStateFlow(listOf(ItemListBlock("a"), ItemListBlock(" "))), BlockType.LIST_BULLET),
            Block.ListBlock(MutableStateFlow(listOf(ItemListBlock("b"))), BlockType.LIST_NUMBER),
            Block.ListBlock(
                MutableStateFlow(listOf(ItemListBlock("c", isCheck = true), ItemListBlock("d"))),
                BlockType.LIST_CHECK,
            ),
        )

        assertEquals(
            listOf(
                DocumentBlock.Heading(1, "One"),
                DocumentBlock.Heading(4, "Four"),
                DocumentBlock.Paragraph("Plain"),
                DocumentBlock.Quote("Said"),
                DocumentBlock.Link("Site", "https://example.org"),
                DocumentBlock.Separator,
                DocumentBlock.Items(ListKind.BULLET, listOf(DocumentItem("a"))),
                DocumentBlock.Items(ListKind.NUMBERED, listOf(DocumentItem("b"))),
                DocumentBlock.Items(ListKind.CHECKLIST, listOf(DocumentItem("c", true), DocumentItem("d", false))),
            ),
            NoteDocument.fromBlocks(blocks),
        )
    }

    @Test
    fun blocksWithoutContentAreLeftOut() {
        assertEquals(listOf(DocumentBlock.Paragraph("kept")), NoteDocument.fromBlocks(listOf(text(" "), text("kept"))))
    }

    @Test
    fun aNoteBecomesADocumentWithItsTitleAndTag() {
        val json = BlockConverter.blocksToJson(listOf(text("Body")))
        val note = Note(id = 1, title = "  Plan ", value = json)

        val document = NoteDocument.of(note, tagName = " Work ", untitled = "Untitled", unreadable = "Unreadable")

        assertEquals("Plan", document.title)
        assertEquals("Work", document.tag)
        assertEquals(listOf(DocumentBlock.Paragraph("Body")), document.blocks)
    }

    @Test
    fun aNoteWithoutATitleOrTagIsNamedAndUntagged() {
        val document = NoteDocument.of(Note(id = 1, title = " ", value = "[]"), null, "Untitled", "Unreadable")

        assertEquals("Untitled", document.title)
        assertNull(document.tag)
        assertEquals(emptyList(), document.blocks)
    }

    @Test
    fun aNoteThatCannotBeReadSaysSo() {
        val document = NoteDocument.of(Note(id = 1, title = "T", value = "{not json"), "", "Untitled", "Unreadable")

        assertEquals(listOf(DocumentBlock.Paragraph("Unreadable")), document.blocks)
        assertNull(document.tag)
    }
}
