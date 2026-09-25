package com.pasich.encly.presentation.components

import android.app.Application
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.core.view.inputmethod.EditorInfoCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What Encly asks of the keyboard, by default and with strict keyboard privacy. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class KeyboardPrivacyTest {

    @Test
    fun everyFieldAsksTheKeyboardNotToLearn() {
        val info = textField(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)

        applyKeyboardPrivacy(info, strict = false)

        assertTrue(info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
        assertEquals(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, info.inputType)
    }

    @Test
    fun strictModeMakesTextFieldsPasswordFieldsWithoutSuggestionsOrSurroundingText() {
        val info = textField(InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        EditorInfoCompat.setInitialSurroundingText(info, "the note around the cursor")

        applyKeyboardPrivacy(info, strict = true)

        assertEquals(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            info.inputType,
        )
        assertTrue(EditorInfoCompat.getInitialTextBeforeCursor(info, 100, 0).isNullOrEmpty())
        assertTrue(info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
    }

    @Test
    fun strictModeLeavesNumberFieldsAlone() {
        val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_NUMBER }

        applyKeyboardPrivacy(info, strict = true)

        assertEquals(InputType.TYPE_CLASS_NUMBER, info.inputType)
    }

    private fun textField(flags: Int) = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or flags }
}
