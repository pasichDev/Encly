package com.pasich.encly.presentation.editor.state

import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistLockTest {
    private val editor = BlockEditorState { 0L }

    private fun checklist() = Block.ListBlock(
        MutableStateFlow(listOf(ItemListBlock("milk"), ItemListBlock("eggs", isCheck = true))),
        blockType = BlockType.LIST_CHECK,
    )

    @Test
    fun lockingIsOneUndoStepAndOnlyForChecklists() {
        val list = checklist()
        val bullets = Block.ListBlock(blockType = BlockType.LIST_BULLET)
        editor.load(listOf(list, bullets))

        assertTrue(editor.toggleChecklistLock(list.id))
        assertTrue(list.locked.value)
        editor.undo()
        assertFalse(list.locked.value)
        editor.redo()
        assertTrue(list.locked.value)

        assertFalse(editor.toggleChecklistLock(bullets.id))
        assertFalse(bullets.locked.value)
    }

    @Test
    fun aLockedChecklistIsNotConvertedByTheToolbar() {
        val list = checklist()
        editor.load(listOf(list))
        editor.selection.onFocused(list.id)
        editor.toggleChecklistLock(list.id)

        editor.applyTool(BlockType.LIST_BULLET)

        assertTrue(editor.blocks.single() === list)
        assertTrue(list.locked.value)
    }

    @Test
    fun aLockedChecklistTakesNoFocusButKeepsItsItems() {
        val list = checklist()
        editor.load(listOf(list))

        editor.toggleChecklistLock(list.id)

        assertFalse(list.canTakeFocus())
        assertEquals(listOf("milk", "eggs"), list.items.value.map { it.value })
    }

    @Test
    fun theLockSurvivesSavingAndAnUnlockedListStoresNoExtraKey() {
        val locked = checklist().also { it.locked.value = true }
        val open = checklist()

        val json = BlockConverter.blocksToJson(listOf(locked, open))
        val back = BlockConverter.jsonToBlocks(json).filterIsInstance<Block.ListBlock>()

        assertEquals(listOf(true, false), back.map { it.locked.value })
        assertEquals(1, Regex("isLocked").findAll(json).count())
    }

    @Test
    fun aNoteWrittenBeforeTheLockOpensUnlocked() {
        val old = """[{"blockType":"LIST_CHECK","items":[{"value":"milk","isCheck":false}]}]"""

        val list = BlockConverter.jsonToBlocks(old).single() as Block.ListBlock

        assertFalse(list.locked.value)
    }

    @Test
    fun aDuplicateKeepsTheLockWithoutSharingIt() {
        val list = checklist().also { it.locked.value = true }
        editor.load(listOf(list))

        editor.duplicateBlockOf(list.id)

        val copy = editor.blocks[1] as Block.ListBlock
        assertTrue(copy.locked.value)
        copy.locked.value = false
        assertTrue(list.locked.value)
    }
}
