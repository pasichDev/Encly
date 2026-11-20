package com.pasich.encly.editor.ui.blocks

import android.app.Application
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A code field tells the keyboard not to suggest or autocorrect, whatever the field asked for before. */
@OptIn(ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NoSuggestionsInputTest {
    @Test
    fun aTextFieldGetsTheNoSuggestionsFlagAndKeepsItsOtherFlags() {
        val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }

        askForNoSuggestions(info)

        assertTrue(info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0)
        assertTrue(info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0)
    }

    @Test
    fun anotherClassOfInputIsLeftAlone() {
        val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_NUMBER }

        askForNoSuggestions(info)

        assertEquals(InputType.TYPE_CLASS_NUMBER, info.inputType)
    }

    @Test
    fun theRequestKeepsTheFieldsConnectionAndFlagsItsInfo() {
        val connection = mock(InputConnection::class.java)
        val request = PlatformTextInputMethodRequest { connection }
        val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }

        val made = noSuggestionsRequest(request).createInputConnection(info)

        assertSame(connection, made)
        assertTrue(info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0)
    }
}
