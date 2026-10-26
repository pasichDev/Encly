package com.pasich.encly.presentation.editor.blocks

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSyncTest {
    @Test
    fun typingIsAnEditAndItsLateEchoIsNotAnExternalChange() {
        val sync = TextSync("a")

        assertTrue(sync.isEdit("ab"))
        assertTrue(sync.isEdit("abc"))
        // The model catches up one edit at a time; by then it holds what was typed last.
        assertFalse(sync.isExternal("abc"))
    }

    @Test
    fun undoIsAnExternalChangeAndSettingTheFieldIsNotAnEdit() {
        val sync = TextSync("abc")

        assertTrue(sync.isExternal("a"))
        assertFalse(sync.isEdit("a"))
    }

    @Test
    fun theSameTextIsNeitherAnEditNorAChange() {
        val sync = TextSync("same")

        assertFalse(sync.isEdit("same"))
        assertFalse(sync.isExternal("same"))
    }

    @Test
    fun textPutInFrontIsInsertedAndKeepsWhatTheFieldHolds() {
        val state = TextFieldState("world", TextRange(2))

        state.replaceWith("Hello world")

        assertEquals("Hello world", state.text.toString())
    }

    @Test
    fun anyOtherChangeReplacesTheTextAndPutsTheCursorAtTheEnd() {
        val state = TextFieldState("abc", TextRange(0))

        state.replaceWith("xyz")

        assertEquals("xyz", state.text.toString())
        assertEquals(3, state.selection.start)
    }
}
