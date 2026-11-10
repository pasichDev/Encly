package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.KeyInjectionScope
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The app's shortcut map: the platform's command key, the chords it owns and the ones it leaves to the fields. */
@OptIn(ExperimentalTestApi::class)
class EnclyShortcutsTest {
    private val commands = mutableListOf<AppCommand>()
    private var handled = true

    private fun ComposeUiTest.show(meta: Boolean) = setContent {
        EnclyShortcutHost(
            onCommand = { command ->
                commands += command
                handled
            },
            shortcuts = EnclyShortcuts(commandKeyIsMeta = meta),
        ) {
            Box(Modifier.size(10.dp))
        }
    }

    private fun ComposeUiTest.press(modifiers: List<Key> = emptyList(), key: Key) {
        onRoot().performKeyInput { chord(modifiers, key) }
        waitForIdle()
    }

    private fun KeyInjectionScope.chord(modifiers: List<Key>, key: Key) {
        if (modifiers.isEmpty()) {
            pressKey(key)
        } else {
            keyDown(modifiers.first())
            chord(modifiers.drop(1), key)
            keyUp(modifiers.first())
        }
    }

    @Test
    fun controlIsTheCommandKeyOutsideMacOs() = runComposeUiTest {
        show(meta = false)

        press(listOf(Key.CtrlLeft), key = Key.N)
        press(listOf(Key.CtrlLeft), key = Key.F)
        press(listOf(Key.CtrlLeft), key = Key.L)
        press(listOf(Key.CtrlLeft), key = Key.W)
        press(listOf(Key.CtrlLeft), key = Key.PageDown)
        press(listOf(Key.CtrlLeft), key = Key.PageUp)
        press(listOf(Key.CtrlLeft, Key.ShiftLeft), key = Key.Backspace)

        assertEquals(
            listOf(
                AppCommand.NewNote,
                AppCommand.FindInNote,
                AppCommand.LockApp,
                AppCommand.CloseTab,
                AppCommand.NextTab,
                AppCommand.PreviousTab,
                AppCommand.DeleteNote,
            ),
            commands,
        )
    }

    @Test
    fun commandIsTheCommandKeyOnAMac() = runComposeUiTest {
        show(meta = true)

        press(listOf(Key.MetaLeft), key = Key.N)
        press(listOf(Key.MetaLeft), key = Key.W)
        press(listOf(Key.MetaLeft, Key.ShiftLeft), key = Key.RightBracket)
        press(listOf(Key.MetaLeft, Key.ShiftLeft), key = Key.LeftBracket)
        press(listOf(Key.MetaLeft, Key.ShiftLeft), key = Key.Backspace)

        assertEquals(
            listOf(
                AppCommand.NewNote,
                AppCommand.CloseTab,
                AppCommand.NextTab,
                AppCommand.PreviousTab,
                AppCommand.DeleteNote,
            ),
            commands,
        )
    }

    @Test
    fun controlTabSwitchesTabsOnEveryPlatform() = runComposeUiTest {
        show(meta = true)

        press(listOf(Key.CtrlLeft), key = Key.Tab)
        press(listOf(Key.CtrlLeft, Key.ShiftLeft), key = Key.Tab)

        assertEquals(listOf(AppCommand.NextTab, AppCommand.PreviousTab), commands)
    }

    @Test
    fun theWrongCommandKeyIsNotOurs() = runComposeUiTest {
        show(meta = true)
        press(listOf(Key.CtrlLeft), key = Key.N)
        press(listOf(Key.CtrlLeft, Key.MetaLeft), key = Key.N)

        assertTrue(commands.isEmpty())
    }

    @Test
    fun theOtherCommandKeyIsNotOursOffTheMac() = runComposeUiTest {
        show(meta = false)

        press(listOf(Key.MetaLeft), key = Key.N)
        press(listOf(Key.CtrlLeft, Key.MetaLeft), key = Key.N)

        assertTrue(commands.isEmpty())
    }

    @Test
    fun chordsWithAltOrAnExtraShiftAreLeftToTheField() = runComposeUiTest {
        show(meta = false)

        press(listOf(Key.CtrlLeft, Key.AltLeft), key = Key.N)
        press(listOf(Key.CtrlLeft, Key.ShiftLeft), key = Key.N)
        press(listOf(Key.CtrlLeft, Key.ShiftLeft), key = Key.PageDown)
        press(listOf(Key.CtrlLeft), key = Key.Backspace)
        press(listOf(Key.CtrlLeft), key = Key.X)
        press(listOf(Key.CtrlLeft, Key.AltLeft), key = Key.Tab)
        press(key = Key.N)

        assertTrue(commands.isEmpty())
    }

    @Test
    fun escapeIsADismissOnlyWhenNothingElseUsedIt() = runComposeUiTest {
        show(meta = false)
        press(key = Key.Escape)
        assertEquals(listOf(AppCommand.Dismiss), commands)
    }

    @Test
    fun escapeWithAModifierIsNotADismiss() = runComposeUiTest {
        show(meta = false)

        press(listOf(Key.ShiftLeft), key = Key.Escape)

        assertTrue(commands.isEmpty())
    }

    @Test
    fun aCommandNobodyHandledIsNotConsumed() = runComposeUiTest {
        handled = false
        show(meta = false)

        press(listOf(Key.CtrlLeft), key = Key.N)

        assertEquals(listOf(AppCommand.NewNote), commands)
    }

    @Test
    fun keyUpIsNotACommand() = runComposeUiTest {
        show(meta = false)

        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.N)
            keyUp(Key.N)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()

        assertEquals(listOf(AppCommand.NewNote), commands)
    }

    @Test
    fun theLabelsAreWhatTheUserReads() {
        val mac = EnclyShortcuts(commandKeyIsMeta = true)
        val other = EnclyShortcuts(commandKeyIsMeta = false)

        assertEquals("⌘N", mac.label(AppCommand.NewNote))
        assertEquals("Ctrl+N", other.label(AppCommand.NewNote))
        assertEquals("⌘F", mac.label(AppCommand.FindInNote))
        assertEquals("⌘L", mac.label(AppCommand.LockApp))
        assertEquals("⌘W", mac.label(AppCommand.CloseTab))
        assertEquals("⌃Tab", mac.label(AppCommand.NextTab))
        assertEquals("Ctrl+Tab", other.label(AppCommand.NextTab))
        assertEquals("⌃⇧Tab", mac.label(AppCommand.PreviousTab))
        assertEquals("Ctrl+Shift+Tab", other.label(AppCommand.PreviousTab))
        assertEquals("⌘⇧⌫", mac.label(AppCommand.DeleteNote))
        assertEquals("Ctrl+Shift+Backspace", other.label(AppCommand.DeleteNote))
        assertEquals("Esc", mac.label(AppCommand.Dismiss))
    }

    @Test
    fun theDefaultCommandKeyIsTheHostsOwn() {
        val isMac = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

        assertEquals(isMac, platformUsesCommandKey())
        assertEquals(if (isMac) "⌘N" else "Ctrl+N", EnclyShortcuts().label(AppCommand.NewNote))
    }
}
