package com.pasich.encly.editor.ui.blocks

import android.app.Application
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The keyboard's Select all, asked while the field's text is all selected already, selects the whole note. */
@OptIn(ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SelectAllConnectionTest {
    private val target = mock(InputConnection::class.java)
    private var asked = 0
    private var takes = true
    private val connection = SelectAllConnection(target) { asked++ >= 0 && takes }

    private fun selecting(text: String, start: Int, end: Int) {
        val extracted = ExtractedText().apply {
            this.text = text
            selectionStart = start
            selectionEnd = end
        }
        `when`(target.getExtractedText(any(ExtractedTextRequest::class.java), eq(0))).thenReturn(extracted)
    }

    @Test
    fun selectAllOverAllSelectedTextTakesTheWholeNoteInstead() {
        selecting("abc", 0, 3)

        assertTrue(connection.performContextMenuAction(android.R.id.selectAll))

        assertEquals(1, asked)
        verify(target, never()).performContextMenuAction(android.R.id.selectAll)
    }

    @Test
    fun aSelectionMadeBackwardsCountsToo() {
        selecting("abc", 3, 0)

        assertTrue(connection.performContextMenuAction(android.R.id.selectAll))
        assertEquals(1, asked)
    }

    @Test
    fun anEmptyFieldHasNothingOfItsOwnToSelect() {
        selecting("", 0, 0)

        assertTrue(connection.performContextMenuAction(android.R.id.selectAll))
        assertEquals(1, asked)
    }

    @Test
    fun partlySelectedTextIsSelectedByTheFieldFirst() {
        `when`(target.performContextMenuAction(android.R.id.selectAll)).thenReturn(true)
        selecting("abc", 1, 3)
        assertTrue(connection.performContextMenuAction(android.R.id.selectAll))

        selecting("abc", 0, 2)
        assertTrue(connection.performContextMenuAction(android.R.id.selectAll))

        selecting("abc", 1, 1)
        assertTrue(connection.performContextMenuAction(android.R.id.selectAll))

        assertEquals(0, asked)
        verify(target, times(3)).performContextMenuAction(android.R.id.selectAll)
    }

    @Test
    fun whenTheEditorDeclinesTheFieldHandlesIt() {
        takes = false
        selecting("abc", 0, 3)
        `when`(target.performContextMenuAction(android.R.id.selectAll)).thenReturn(true)

        assertTrue(connection.performContextMenuAction(android.R.id.selectAll))

        assertEquals(1, asked)
        verify(target).performContextMenuAction(android.R.id.selectAll)
    }

    @Test
    fun aKeyboardThatSaysNothingAboutTheTextIsLeftToTheField() {
        `when`(target.performContextMenuAction(android.R.id.selectAll)).thenReturn(false)

        assertFalse(connection.performContextMenuAction(android.R.id.selectAll))

        assertEquals(0, asked)
    }

    @Test
    fun otherMenuActionsGoStraightToTheField() {
        `when`(target.performContextMenuAction(android.R.id.copy)).thenReturn(true)

        assertTrue(connection.performContextMenuAction(android.R.id.copy))

        assertEquals(0, asked)
        verify(target, never()).getExtractedText(any(), anyInt())
    }

    @Test
    fun theRequestWrapsTheConnectionTheFieldMakes() {
        val info = EditorInfo()
        val request = PlatformTextInputMethodRequest { given ->
            assertSame(info, given)
            target
        }

        val made = selectAllRequest(request) { true }.createInputConnection(info)

        assertTrue(made is SelectAllConnection)
    }
}
