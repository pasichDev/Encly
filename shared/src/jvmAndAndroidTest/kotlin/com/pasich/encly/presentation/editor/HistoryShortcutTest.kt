package com.pasich.encly.presentation.editor

import androidx.compose.ui.input.key.Key
import com.pasich.encly.presentation.screen.editnote.HistoryShortcut
import com.pasich.encly.presentation.screen.editnote.historyShortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryShortcutTest {
    @Test
    fun controlOrCommandZIsUndo() {
        assertEquals(HistoryShortcut.UNDO, historyShortcut(Key.Z, ctrlOrMeta = true, shift = false))
    }

    @Test
    fun shiftZAndYAreRedo() {
        assertEquals(HistoryShortcut.REDO, historyShortcut(Key.Z, ctrlOrMeta = true, shift = true))
        assertEquals(HistoryShortcut.REDO, historyShortcut(Key.Y, ctrlOrMeta = true, shift = false))
    }

    @Test
    fun withoutControlOrCommandTheKeysAreTyping() {
        assertNull(historyShortcut(Key.Z, ctrlOrMeta = false, shift = false))
        assertNull(historyShortcut(Key.Y, ctrlOrMeta = false, shift = true))
    }

    @Test
    fun otherShortcutsAreNotHistory() {
        assertNull(historyShortcut(Key.F, ctrlOrMeta = true, shift = false))
        assertNull(historyShortcut(Key.X, ctrlOrMeta = true, shift = false))
    }
}
