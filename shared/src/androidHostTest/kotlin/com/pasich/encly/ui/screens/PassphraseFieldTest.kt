package com.pasich.encly.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.passphrase_caps_lock
import com.pasich.encly.core.resources.passphrase_hide
import com.pasich.encly.core.resources.passphrase_show
import com.pasich.encly.platform.LocalVaultTextKeyboardType
import com.pasich.encly.presentation.screen.pincode.PassphraseField
import com.pasich.encly.presentation.screen.pincode.PassphraseInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The masked passphrase field: focus, Enter, the eye, the Caps Lock hint, and no more text than any passphrase. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class PassphraseFieldTest : ComposeScreenTest() {
    private val input = PassphraseInput()
    private var submitted = 0
    private var edits = 0
    private var capsLock: Boolean? = null

    /** The system clipboard of the screen: whatever the field tries to put on it is counted. */
    private val clipboard = RecordingClipboard()
    private var enabled by mutableStateOf(true)

    @OptIn(ExperimentalComposeUiApi::class)
    private class RecordingClipboard : Clipboard {
        var writes = 0
        override val nativeClipboard: NativeClipboard get() = error("the field has no business with the platform")

        override suspend fun getClipEntry(): ClipEntry? = null

        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            writes++
        }
    }

    private val field get() = rule.onNodeWithContentDescription(LABEL)

    private fun show() {
        setScreen {
            // What the desktop host gives: the plain text keyboard, which is not a password type by itself.
            CompositionLocalProvider(LocalVaultTextKeyboardType provides KeyboardType.Text) {
                CompositionLocalProvider(LocalClipboard provides clipboard) {
                    PassphraseField(
                        input = input,
                        label = LABEL,
                        onSubmit = { submitted++ },
                        enabled = enabled,
                        onEdit = { edits++ },
                        capsLock = { capsLock },
                    )
                }
            }
        }
    }

    @Test
    fun itTakesFocusWhenItAppears() {
        show()

        field.assertIsFocused()
    }

    @Test
    fun whatIsTypedGoesToTheInputAndEveryEditIsReported() {
        show()

        field.performTextInput("correct horse")

        assertEquals("correct horse", input.text)
        assertTrue(edits > 0)
    }

    @Test
    fun theTextIsMaskedUntilTheEyeIsPressed() {
        show()
        val masked = SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)
        field.performTextInput("correct horse")

        assertEquals(1, rule.onAllNodes(masked).fetchSemanticsNodes().size)
        rule.onNodeWithContentDescription(str(Res.string.passphrase_show)).performClick()

        assertTrue(input.revealed)
        // The semantics stay "password" in clear too: a screen reader is not handed the passphrase.
        assertEquals(1, rule.onAllNodes(masked).fetchSemanticsNodes().size)
        rule.onNodeWithContentDescription(str(Res.string.passphrase_hide)).performClick()
        assertFalse(input.revealed)
    }

    @Test
    fun enterSubmitsOnceAndNothingIsSubmittedByItself() {
        show()
        field.performTextInput("correct horse")
        assertEquals(0, submitted)

        field.performKeyInput { pressKey(Key.Enter) }

        assertEquals(1, submitted)
    }

    @Test
    fun theNumpadEnterSubmitsToo() {
        show()

        field.performKeyInput { pressKey(Key.NumPadEnter) }

        assertEquals(1, submitted)
    }

    @Test
    fun theKeyboardsDoneActionSubmits() {
        show()
        field.performTextInput("correct horse")

        field.performImeAction()

        assertEquals(1, submitted)
    }

    @Test
    fun anotherKeyDoesNotSubmit() {
        show()

        field.performKeyInput { pressKey(Key.A) }

        assertEquals(0, submitted)
    }

    @Test
    fun aPasteLongerThanAnyPassphraseIsCut() {
        show()

        field.performTextInput("x".repeat(PassphraseInput.MAX_TYPED_CHARS + 40))

        assertEquals(PassphraseInput.MAX_TYPED_CHARS, input.text.length)
    }

    @Test
    fun aDisabledFieldTakesNoInputAndTakesFocusBackWhenEnabled() {
        enabled = false
        show()
        field.assertIsNotEnabled()

        rule.runOnIdle { enabled = true }
        rule.waitForIdle()

        field.assertIsFocused()
    }

    @Test
    fun theCapsLockHintShowsWhileTheKeyIsOnAndTheFieldHasFocus() {
        capsLock = true
        show()

        rule.onNodeWithText(str(Res.string.passphrase_caps_lock)).assertIsDisplayed()
    }

    @Test
    fun theHintFollowsTheKeyAsItIsPressed() {
        show()
        assertEquals(0, countText(str(Res.string.passphrase_caps_lock)))

        capsLock = true
        field.performKeyInput { pressKey(Key.A) }
        rule.waitForIdle()

        assertEquals(1, countText(str(Res.string.passphrase_caps_lock)))
    }

    @Test
    fun noHintWhereTheHostCannotTellOrTheKeyIsOff() {
        capsLock = null
        show()
        assertEquals(0, countText(str(Res.string.passphrase_caps_lock)))

        capsLock = false
        field.performKeyInput { pressKey(Key.A) }
        assertEquals(0, countText(str(Res.string.passphrase_caps_lock)))
    }

    private companion object {
        const val LABEL = "Passphrase"
    }
}
