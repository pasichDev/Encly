package com.pasich.encly.editor.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.text.TextRange
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.state.SelectedBlocks
import com.pasich.encly.editor.ui.blocks.coversWholeText
import com.pasich.encly.editor.ui.blocks.isSelectAllKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectionKeysTest {
    private fun key(
        key: Key,
        command: Boolean = false,
        shift: Boolean = false,
        type: KeyEventType = KeyEventType.KeyDown,
    ) = selectionKeyOf(key, type, command, shift)

    @Test
    fun theKeysOfBlockSelectionAreRecognised() {
        assertEquals(SelectionKey.LEAVE, key(Key.Escape))
        assertEquals(SelectionKey.EXTEND_UP, key(Key.DirectionUp, shift = true))
        assertEquals(SelectionKey.EXTEND_DOWN, key(Key.DirectionDown, shift = true))
        assertEquals(SelectionKey.DELETE, key(Key.Delete))
        assertEquals(SelectionKey.DELETE, key(Key.Backspace))
        assertEquals(SelectionKey.SELECT_ALL, key(Key.A, command = true))
        assertEquals(SelectionKey.COPY, key(Key.C, command = true))
        assertEquals(SelectionKey.COPY_MARKDOWN, key(Key.C, command = true, shift = true))
        assertEquals(SelectionKey.CUT, key(Key.X, command = true))
    }

    @Test
    fun otherKeysAndKeysComingUpAreNotSelectionKeys() {
        assertNull(key(Key.DirectionUp))
        assertNull(key(Key.A))
        assertNull(key(Key.C))
        assertNull(key(Key.X))
        assertNull(key(Key.V, command = true))
        assertNull(key(Key.Escape, type = KeyEventType.KeyUp))
        assertNull(key(Key.Delete, type = KeyEventType.KeyUp))
    }

    // --- Select all pressed twice ------------------------------------------------------------------

    @Test
    fun selectAllIsAWithControlOrCommandAndNoShiftGoingDown() {
        assertTrue(isSelectAllKey(Key.A, KeyEventType.KeyDown, command = true, shift = false))
        assertFalse(isSelectAllKey(Key.A, KeyEventType.KeyDown, command = false, shift = false))
        assertFalse(isSelectAllKey(Key.A, KeyEventType.KeyDown, command = true, shift = true))
        assertFalse(isSelectAllKey(Key.A, KeyEventType.KeyUp, command = true, shift = false))
        assertFalse(isSelectAllKey(Key.B, KeyEventType.KeyDown, command = true, shift = false))
    }

    @Test
    fun theFirstPressSelectsTheFieldAndTheSecondTheNote() {
        assertTrue(coversWholeText(TextRange(0, 5), 5))
        assertTrue(coversWholeText(TextRange(5, 0), 5))
        assertFalse(coversWholeText(TextRange(1, 5), 5))
        assertFalse(coversWholeText(TextRange(0, 4), 5))
        assertFalse(coversWholeText(TextRange(2), 5))
        // An empty field has nothing of its own to select first.
        assertTrue(coversWholeText(TextRange(0), 0))
    }

    // --- what a key does ---------------------------------------------------------------------------

    private val blocks = List(3) { Block.TextBlock(MutableStateFlow("b$it")) }
    private val selected = SelectedBlocks(blocks)
    private val calls = mutableListOf<String>()

    private fun actions(canEdit: Boolean) = SelectedBlocksActions(
        canEdit = canEdit,
        onCopy = { calls += "copy" },
        onCopyMarkdown = { calls += "markdown" },
        onCut = { calls += "cut" },
        onDelete = { calls += "delete" },
    )

    @Test
    fun keysMoveAndLeaveTheSelection() {
        selected.select(blocks[1].id)

        SelectionKey.EXTEND_DOWN.perform(selected, actions(true))
        assertEquals(setOf(blocks[1].id, blocks[2].id), selected.ids.value)
        SelectionKey.EXTEND_UP.perform(selected, actions(true))
        assertEquals(setOf(blocks[1].id), selected.ids.value)
        SelectionKey.SELECT_ALL.perform(selected, actions(true))
        assertEquals(3, selected.count)
        SelectionKey.LEAVE.perform(selected, actions(true))
        assertFalse(selected.isActive)
    }

    @Test
    fun copyIsAlwaysOfferedAndCutAndDeleteOnlyWhereTheNoteCanChange() {
        SelectionKey.COPY.perform(selected, actions(false))
        SelectionKey.COPY_MARKDOWN.perform(selected, actions(false))
        SelectionKey.CUT.perform(selected, actions(false))
        SelectionKey.DELETE.perform(selected, actions(false))
        assertEquals(listOf("copy", "markdown"), calls)

        SelectionKey.CUT.perform(selected, actions(true))
        SelectionKey.DELETE.perform(selected, actions(true))
        assertEquals(listOf("copy", "markdown", "cut", "delete"), calls)
    }
}
