package com.pasich.encly.presentation.screen.pincode

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.KeyInjectionScope
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.passphrase_caps_lock
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.platform.LocalVaultTextKeyboardType
import com.pasich.encly.presentation.designsystem.EnclyTextField
import com.pasich.encly.ui.theme.EnclyTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The passphrase field with the desktop's keyboard: a real Enter key, the plain text type, the Caps Lock hint. */
@OptIn(ExperimentalTestApi::class)
class PassphraseFieldDesktopTest {
    private val input = PassphraseInput()
    private var submitted = 0
    private var capsLock: Boolean? = null

    private fun ComposeUiTest.show() = setContent {
        EnclyTheme(settings = ThemeSettings(), dark = false) {
            // What the desktop host provides: the plain text keyboard, not the ASCII-only password one.
            CompositionLocalProvider(LocalVaultTextKeyboardType provides KeyboardType.Text) {
                PassphraseField(
                    input = input,
                    label = LABEL,
                    onSubmit = { submitted++ },
                    capsLock = { capsLock },
                )
            }
        }
    }

    @Test
    fun theFieldTakesFocusAndTextInAnyScript() = runComposeUiTest {
        show()

        onNodeWithContentDescription(LABEL).assertIsFocused()
        onNodeWithContentDescription(LABEL).performTextInput("парольна фраза 密码短语")

        assertEquals("парольна фраза 密码短语", input.text)
    }

    @Test
    fun enterSubmitsExactlyOnce() = runComposeUiTest {
        show()
        onNodeWithContentDescription(LABEL).performTextInput("correct horse battery")

        onNodeWithContentDescription(LABEL).performKeyInput { pressKey(Key.Enter) }

        assertEquals(1, submitted)
    }

    @Test
    fun theCapsLockHintShowsWhileTheKeyIsOn() = runComposeUiTest {
        capsLock = true
        show()

        onNodeWithContentDescription(LABEL).performKeyInput { pressKey(Key.A) }
        waitForIdle()

        assertTrue(onAllNodesWithTextCount(runBlocking { getString(Res.string.passphrase_caps_lock) }) == 1)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private class RecordingClipboard : Clipboard {
        var writes = 0
        override val nativeClipboard: NativeClipboard get() = error("not used")

        override suspend fun getClipEntry(): ClipEntry? = null

        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            writes++
        }
    }

    private fun KeyInjectionScope.chord(modifier: Key, keys: List<Key>) {
        keyDown(modifier)
        keys.forEach { pressKey(it) }
        keyUp(modifier)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun aPlainFieldCopiesToTheClipboardSoTheNextTestsMeanSomething() = runComposeUiTest {
        val clipboard = RecordingClipboard()
        var text by mutableStateOf("some text to copy")
        setContent {
            EnclyTheme(settings = ThemeSettings(), dark = false) {
                CompositionLocalProvider(LocalClipboard provides clipboard) {
                    EnclyTextField(value = text, onValueChange = {
                        text = it
                    }, label = "Plain", fieldModifier = Modifier.testTag("plain"))
                }
            }
        }
        onNodeWithTag("plain").performTextInputSelection(TextRange(0, text.length))

        onNodeWithTag("plain").performKeyInput {
            // The copy key is Ctrl on Linux and Windows and Command on a Mac.
            chord(Key.CtrlLeft, listOf(Key.C))
            chord(Key.MetaLeft, listOf(Key.C))
        }
        waitForIdle()

        assertTrue(clipboard.writes > 0)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun noCopyOrCutOfASelectedPassphraseReachesTheClipboardByKeyMaskedOrInClear() = runComposeUiTest {
        val clipboard = RecordingClipboard()
        showWith(clipboard)
        onNodeWithContentDescription(LABEL).performTextInput("correct horse battery")
        onNodeWithContentDescription(LABEL).performTextInputSelection(TextRange(0, input.text.length))

        listOf(false, true).forEach { revealed ->
            input.revealed = revealed
            waitForIdle()
            onNodeWithContentDescription(LABEL).performKeyInput {
                chord(Key.CtrlLeft, listOf(Key.C, Key.X, Key.Insert))
                chord(Key.MetaLeft, listOf(Key.C, Key.X))
                chord(Key.ShiftLeft, listOf(Key.Delete))
            }
            waitForIdle()

            assertEquals(0, clipboard.writes)
            assertEquals("correct horse battery", input.text)
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun noCopyOrCutOfASelectedPassphraseReachesTheClipboardByTheMenu() = runComposeUiTest {
        val clipboard = RecordingClipboard()
        showWith(clipboard)
        onNodeWithContentDescription(LABEL).performTextInput("correct horse battery")
        input.revealed = true
        onNodeWithContentDescription(LABEL).performTextInputSelection(TextRange(0, input.text.length))
        waitForIdle()

        onNodeWithContentDescription(LABEL).performMouseInput { rightClick() }
        waitForIdle()

        assertEquals(0, menuEntries())
        assertEquals(0, clipboard.writes)
        assertEquals("correct horse battery", input.text)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun ComposeUiTest.showWith(clipboard: Clipboard) = setContent {
        EnclyTheme(settings = ThemeSettings(), dark = false) {
            CompositionLocalProvider(
                LocalVaultTextKeyboardType provides KeyboardType.Text,
                LocalClipboard provides clipboard,
            ) {
                PassphraseField(input = input, label = LABEL, onSubmit = { submitted++ }, capsLock = { capsLock })
            }
        }
    }

    private fun ComposeUiTest.menuEntries(): Int = listOf("Copy", "Cut", "Paste", "Select all", "Select All")
        .sumOf { onAllNodes(hasText(it)).fetchSemanticsNodes().size }

    @Test
    fun aPlainFieldOpensAContextMenuOnRightClickSoTheNextTestMeansSomething() = runComposeUiTest {
        var text by mutableStateOf("some text to copy")
        setContent {
            EnclyTheme(settings = ThemeSettings(), dark = false) {
                EnclyTextField(value = text, onValueChange = {
                    text = it
                }, label = "Plain", fieldModifier = Modifier.testTag("plain"))
            }
        }

        onNodeWithTag("plain").performMouseInput { rightClick() }
        waitForIdle()

        assertTrue(menuEntries() > 0)
    }

    @Test
    fun theRevealedPassphraseFieldOpensNoContextMenu() = runComposeUiTest {
        show()
        onNodeWithContentDescription(LABEL).performTextInput("correct horse battery")
        input.revealed = true
        waitForIdle()

        onNodeWithContentDescription(LABEL).performMouseInput { rightClick() }
        waitForIdle()

        assertEquals(0, menuEntries())
    }

    private fun ComposeUiTest.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(hasText(text)).fetchSemanticsNodes().size

    private companion object {
        const val LABEL = "Passphrase"
    }
}
