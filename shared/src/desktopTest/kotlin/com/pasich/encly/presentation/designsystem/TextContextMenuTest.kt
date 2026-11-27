package com.pasich.encly.presentation.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.select_all
import com.pasich.encly.core.resources.selection_copy
import com.pasich.encly.core.resources.selection_cut
import com.pasich.encly.core.resources.selection_paste
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.platform.LocalVaultTextKeyboardType
import com.pasich.encly.presentation.screen.pincode.PassphraseField
import com.pasich.encly.presentation.screen.pincode.PassphraseInput
import com.pasich.encly.ui.theme.EnclyTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.awt.datatransfer.Clipboard as AwtClipboard

/**
 * What a right click in a text field of the desktop offers: the passphrase field takes text in and gives none out,
 * an ordinary field has the whole menu. The labels are the app's own, read from the string resources.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class TextContextMenuTest {
    /** A clipboard over a real AWT one, which is what the text fields ask whether there is text to paste. */
    private class AwtBackedClipboard(text: String) : Clipboard {
        private val board = AwtClipboard("test").apply { setContents(StringSelection(text), null) }

        override val nativeClipboard: NativeClipboard get() = board

        override suspend fun getClipEntry(): ClipEntry? = ClipEntry(board.getContents(null))

        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            (clipEntry?.nativeClipEntry as? Transferable)?.let { board.setContents(it, null) }
        }
    }

    private fun label(key: StringResource) = runBlocking { getString(key) }

    private fun ComposeUiTest.rows(key: StringResource): Int =
        onAllNodes(hasText(label(key))).fetchSemanticsNodes().size

    private fun ComposeUiTest.showPassphrase(input: PassphraseInput, clipboard: Clipboard) = setContent {
        EnclyTheme(settings = ThemeSettings(), dark = false) {
            CompositionLocalProvider(
                LocalVaultTextKeyboardType provides KeyboardType.Text,
                LocalClipboard provides clipboard,
            ) {
                PassphraseField(input = input, label = LABEL, onSubmit = {})
            }
        }
    }

    @Test
    fun aRightClickOnThePassphraseFieldOffersPasteAndNoCopyOrCut() = runComposeUiTest {
        val input = PassphraseInput()
        showPassphrase(input, AwtBackedClipboard("from the password manager"))

        onNodeWithContentDescription(LABEL).performMouseInput { rightClick() }
        waitForIdle()

        assertEquals(1, rows(Res.string.selection_paste))
        assertEquals(0, rows(Res.string.selection_copy))
        assertEquals(0, rows(Res.string.selection_cut))
    }

    @Test
    fun pastingFromTheMenuPutsTheClipboardTextInThePassphraseField() = runComposeUiTest {
        val input = PassphraseInput()
        showPassphrase(input, AwtBackedClipboard("from the password manager"))

        onNodeWithContentDescription(LABEL).performMouseInput { rightClick() }
        waitForIdle()
        onNode(hasText(label(Res.string.selection_paste))).performClick()
        waitUntil(timeoutMillis = 3_000) { input.text.isNotEmpty() }

        assertEquals("from the password manager", input.text)
    }

    @Test
    fun theMenuOfASelectedPassphraseStillOffersNoCopyOrCut() = runComposeUiTest {
        val input = PassphraseInput()
        showPassphrase(input, AwtBackedClipboard("from the password manager"))
        input.edit("correct horse battery")
        input.revealed = true
        onNodeWithContentDescription(LABEL).performTextInputSelection(TextRange(0, input.text.length))
        waitForIdle()

        onNodeWithContentDescription(LABEL).performMouseInput { rightClick() }
        waitForIdle()

        assertEquals(1, rows(Res.string.selection_paste))
        assertEquals(0, rows(Res.string.selection_copy))
        assertEquals(0, rows(Res.string.selection_cut))
    }

    @Test
    fun aRightClickOnAnOrdinaryFieldOffersCutCopyPasteAndSelectAll() = runComposeUiTest {
        var text by mutableStateOf("some text to copy")
        setContent {
            EnclyTheme(settings = ThemeSettings(), dark = false) {
                CompositionLocalProvider(LocalClipboard provides AwtBackedClipboard("pasted")) {
                    EnclyTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = "Plain",
                        fieldModifier = Modifier.testTag("plain"),
                    )
                }
            }
        }
        onNodeWithTag("plain").performTextInputSelection(TextRange(0, 4))
        waitForIdle()

        onNodeWithTag("plain").performMouseInput { rightClick() }
        waitForIdle()

        assertTrue(rows(Res.string.selection_copy) >= 1)
        assertTrue(rows(Res.string.selection_cut) >= 1)
        assertEquals(1, rows(Res.string.selection_paste))
        assertEquals(1, rows(Res.string.select_all))
    }

    private companion object {
        const val LABEL = "Passphrase"
    }
}
