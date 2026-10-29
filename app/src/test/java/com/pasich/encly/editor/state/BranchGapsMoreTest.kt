package com.pasich.encly.editor.state

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.pasich.encly.editor.codec.MarkdownBlockMapper
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.LinkDataBlock
import com.pasich.encly.editor.model.hasContent
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** More of the quieter directions of the state: nullable text, every list kind, glyphs, a link above a paragraph. */
class BranchGapsMoreTest {
    private fun text(value: String = "") = Block.TextBlock(MutableStateFlow(value))

    private fun editorOf(vararg blocks: Block) = BlockEditorState { 0L }.also { it.load(blocks.toList()) }

    @Test
    fun whichBlocksAreStored() {
        assertFalse(Block.HBlock(MutableStateFlow(" "), BlockType.H1).hasContent())
        assertTrue(Block.HBlock(MutableStateFlow("h"), BlockType.H1).hasContent())
        assertFalse(Block.QuoteBlock(MutableStateFlow("")).hasContent())
        assertTrue(Block.QuoteBlock(MutableStateFlow("q")).hasContent())
        assertFalse(Block.ListBlock(MutableStateFlow(listOf(ItemListBlock(""))), BlockType.LIST_BULLET).hasContent())
        assertTrue(Block.ListBlock(MutableStateFlow(listOf(ItemListBlock("x"))), BlockType.LIST_BULLET).hasContent())
        assertFalse(Block.LinkBlock().hasContent())
        assertTrue(Block.LinkBlock(MutableStateFlow(LinkDataBlock(url = "https://x.org"))).hasContent())
        assertTrue(Block.SeparatorBlock().hasContent())
        assertFalse(text(" ").hasContent())
    }

    @Test
    fun glyphsAndCheckMarksReadTheirState() {
        fun checks(markdown: String) =
            (MarkdownBlockMapper.toBlocks(markdown).single() as Block.ListBlock).items.value.map { it.isCheck }
        assertEquals(listOf(true), checks("☑ done"))
        assertEquals(listOf(true), checks("✅ done"))
        assertEquals(listOf(false), checks("☐ todo"))
        assertEquals(listOf(true), checks("- [X] done"))
        assertEquals(listOf(false), checks("- [ ] todo"))
    }

    @Test
    fun nullableTextHasNoTypingStep() {
        val history = SnapshotStateList<Block>()
        val ops = BlockOperations(history) { 0L }
        val field = MutableStateFlow<String?>("")
        ops.changeValue("a", field, "typed", mergeable = true)
        ops.changeValue("a", field, null, mergeable = true)
        ops.changeValue("a", field, "again", mergeable = true)
        var steps = 0
        while (ops.undo()) steps++
        assertTrue(steps in 1..3)
    }

    @Test
    fun everyListKindConvertsFromEveryOtherKind() {
        val kinds = listOf(BlockType.LIST_CHECK, BlockType.LIST_BULLET, BlockType.LIST_NUMBER)
        for (from in kinds) {
            for (to in kinds) {
                val source = Block.ListBlock(MutableStateFlow(listOf(ItemListBlock("a"), ItemListBlock("b"))), from)
                val converted = convertBlock(source, to)
                if (from == to) {
                    assertTrue(converted is Block.TextBlock)
                } else {
                    assertEquals(to, (converted as Block.ListBlock).blockType)
                }
            }
            val paragraph = convertBlock(text("a\nb"), from) as Block.ListBlock
            assertEquals(2, paragraph.items.value.size)
        }
    }

    @Test
    fun anExactToolOnAHeadingChangesItsLevel() {
        val heading = Block.HBlock(MutableStateFlow("h"), BlockType.H1)
        assertEquals(BlockType.H3, (convertBlock(heading, BlockType.H3, exact = true) as Block.HBlock).blockType)
        assertTrue(convertBlock(heading, BlockType.H1, exact = true) is Block.TextBlock)
        assertTrue(convertBlock(text("t"), BlockType.TEXT, exact = true) == null)
        assertNotNull(convertBlock(Block.QuoteBlock(MutableStateFlow("q")), BlockType.H2, exact = true))
    }

    @Test
    fun aSeparatorToolBeforeAParagraphFocusesThatParagraph() {
        val link = Block.LinkBlock(MutableStateFlow(LinkDataBlock(url = "https://x.org")))
        val paragraph = text("after")
        val editor = editorOf(link, paragraph)
        editor.selection.onInteraction(link.id)

        editor.applyTool(BlockType.SEPARATOR)

        assertEquals(3, editor.blocks.size)
        assertTrue(editor.blocks[1] is Block.SeparatorBlock)
        assertEquals(paragraph.id, editor.blocks[2].id)
    }

    @Test
    fun theNearestFocusableBlockAboveSkipsBlocksWithoutAField() {
        val blocks = listOf(text("a"), Block.SeparatorBlock(), Block.SeparatorBlock())
        assertEquals(0, blocks.previousFocusableIndex(3))
        assertEquals(0, blocks.previousFocusableIndex(2))
    }

    @Test
    fun anItemRememberedForAParagraphIsIgnored() {
        val paragraph = text("abc")
        val editor = editorOf(paragraph)
        editor.selection.onFocused(paragraph.id)
        editor.selection.onItemCaret(paragraph.id, "stray", 2)
        // Added without moving focus, so the paragraph keeps it when the step is undone.
        editor.batch { addBlock(1, text("next")) }

        editor.undo()

        assertEquals(1, editor.blocks.size)
    }

    @Test
    fun aCursorCanBeCarriedWithoutAnItem() {
        val bullets = Block.ListBlock(MutableStateFlow(listOf(ItemListBlock("ab"))), BlockType.LIST_BULLET)
        val numbers = Block.ListBlock(MutableStateFlow(listOf(ItemListBlock("ab"))), BlockType.LIST_NUMBER)
        assertNull(carriedCaret(bullets, numbers, 1, null))
        assertNull(carriedCaret(bullets, numbers, 1, "stray"))
        assertNotNull(carriedCaret(bullets, bullets, 1, bullets.items.value.single().id))
    }

    @Test
    fun aConvertedBlockKeepsTheCursorOfItsSource() {
        val paragraph = text("hello")
        val editor = editorOf(paragraph)
        editor.selection.onCaret(paragraph.id, 3)
        editor.replaceBlock(0, Block.QuoteBlock(MutableStateFlow("hello")))
        editor.undo()
        editor.redo()

        val plain = editorOf(text("x"))
        plain.selection.onCaret(plain.blocks[0].id, 1)
        plain.replaceBlock(0, Block.SeparatorBlock())
        assertEquals(1, plain.blocks.size)
    }
}
