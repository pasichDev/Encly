package com.pasich.encly.presentation.editor.state

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.pasich.encly.dynamicBlocks.Block
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockOperationsTest {
    private var now = 0L
    private val block = Block.TextBlock()
    private val blocks = SnapshotStateList<Block>().apply { add(block) }
    private val operations = BlockOperations(blocks) { now }

    @Test
    fun typingWithinTheMergeWindowIsOneUndoStep() {
        "hello".forEachIndexed { i, _ ->
            operations.changeValue(block.id, block.text, "hello".take(i + 1), mergeable = true)
            now += 100
        }

        assertTrue(operations.undo())
        assertEquals("", block.text.value)
        assertFalse(operations.canUndo())
    }

    @Test
    fun aPauseStartsANewUndoStep() {
        operations.changeValue(block.id, block.text, "one", mergeable = true)
        now += BlockOperations.MERGE_WINDOW_MS + 1
        operations.changeValue(block.id, block.text, "one two", mergeable = true)

        operations.undo()

        assertEquals("one", block.text.value)
    }

    @Test
    fun editsAfterAnUndoAreNotMergedIntoTheStepBelow() {
        operations.changeValue(block.id, block.text, "a", mergeable = true)
        operations.addBlock(1, Block.SeparatorBlock())
        operations.undo()

        operations.changeValue(block.id, block.text, "ab", mergeable = true)
        operations.undo()

        assertEquals("a", block.text.value)
    }

    @Test
    fun historyIsCapped() {
        repeat(BlockOperations.MAX_HISTORY + 50) { i ->
            operations.changeValue(block.id, block.text, "v$i", mergeable = false)
        }

        var undone = 0
        while (operations.undo()) undone++

        assertEquals(BlockOperations.MAX_HISTORY, undone)
    }

    @Test
    fun contentUndoFollowsTheBlockNotItsPosition() {
        val other = Block.TextBlock(MutableStateFlow("other"))
        operations.changeValue(block.id, block.text, "mine", mergeable = false)
        operations.addBlock(0, other)

        // Undo the insert, then the edit: the edit still lands on its own block.
        operations.undo()
        operations.undo()

        assertEquals("", block.text.value)
        assertEquals("other", other.text.value)
    }

    @Test
    fun eachWordTypedIsItsOwnUndoStep() {
        var text = ""
        "one two".forEach { char ->
            text += char
            operations.changeValue(block.id, block.text, text, mergeable = true)
            now += 50
        }

        operations.undo()
        assertEquals("one ", block.text.value)
        operations.undo()
        assertEquals("", block.text.value)
    }

    @Test
    fun deletingAfterTypingIsItsOwnStep() {
        operations.changeValue(block.id, block.text, "abc", mergeable = true)
        now += 50
        operations.changeValue(block.id, block.text, "ab", mergeable = true)

        operations.undo()

        assertEquals("abc", block.text.value)
    }

    @Test
    fun aBatchIsUndoneAndRedoneAsOneStep() {
        val added = Block.TextBlock(MutableStateFlow("world"))
        operations.changeValue(block.id, block.text, "hello world", mergeable = false)

        operations.batch {
            setValue(block.id, block.text, "hello")
            addBlock(1, added)
        }
        assertEquals(listOf("hello", "world"), blocks.map { (it as Block.TextBlock).text.value })

        operations.undo()
        assertEquals(listOf("hello world"), blocks.map { (it as Block.TextBlock).text.value })
        operations.redo()
        assertEquals(listOf("hello", "world"), blocks.map { (it as Block.TextBlock).text.value })
    }

    @Test
    fun anEmptyBatchRecordsNothing() {
        operations.batch { setValue(block.id, block.text, block.text.value) }

        assertFalse(operations.canUndo())
    }
}
