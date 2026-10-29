package com.pasich.encly.editor.state

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.pasich.encly.editor.codec.ImportBlocks
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What tells the host a note changed, the default clock, and the builders the importers use. */
class ContentChangesTest {
    @Test
    fun everyKindOfBlockReportsItsContentAndAFlatNoteStillReports() = runTest {
        val editor = BlockEditorState()
        editor.load(
            listOf(
                Block.TextBlock(),
                Block.HBlock(blockType = BlockType.H1),
                Block.QuoteBlock(),
                Block.LinkBlock(),
                Block.ListBlock(blockType = BlockType.LIST_CHECK),
                Block.SeparatorBlock(),
            ),
        )
        editor.contentChanges.first()

        editor.load(listOf(Block.SeparatorBlock()))
        editor.contentChanges.first()
    }

    @Test
    fun theDefaultClockTellsTime() {
        assertTrue(currentTimeMillis() > 0L)
        val operations = BlockOperations(SnapshotStateList())
        assertTrue(!operations.canUndo())
    }

    @Test
    fun importersBuildBlocksOfEveryKind() {
        assertTrue(ImportBlocks.heading("h", 3) is Block.HBlock)
        assertTrue(ImportBlocks.text("t") is Block.TextBlock)
        assertTrue(ImportBlocks.quote("q") is Block.QuoteBlock)
        val list = ImportBlocks.list(BlockType.LIST_BULLET, listOf(ItemListBlock("a"))) as Block.ListBlock
        assertEquals(BlockType.LIST_BULLET, list.blockType)
        assertEquals(2, ImportBlocks.paragraphs("one\n\ntwo").size)
        assertTrue(ImportBlocks.paragraphs("  ").isEmpty())
    }
}
