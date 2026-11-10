package com.pasich.encly.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.pasich.encly.desktop.storage.DesktopOs
import com.pasich.encly.desktop.window.Accelerator
import com.pasich.encly.desktop.window.DesktopShortcuts
import com.pasich.encly.presentation.designsystem.AppCommand
import com.pasich.encly.presentation.designsystem.EnclyShortcutHost
import com.pasich.encly.presentation.designsystem.EnclyShortcuts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The menu bar and the keyboard host share one key map and never both take a chord: a chord is the menu's
 * (its accelerator is registered, the host declines the command) or the host's (the menu registers nothing for it).
 * Both ways round are checked for every platform, so a new menu item or a new command cannot make a chord run twice.
 */
@OptIn(ExperimentalTestApi::class)
class MenuKeyboardOwnershipTest {
    // The only way to build a key event without a window; the API is for compose-ui itself, a test may use it.
    @OptIn(InternalComposeUiApi::class)
    private fun Accelerator.asEvent(): KeyEvent = KeyEvent(
        key = key,
        type = KeyEventType.KeyDown,
        isCtrlPressed = ctrl,
        isMetaPressed = meta,
        isAltPressed = alt,
        isShiftPressed = shift,
    )

    /** Every accelerator the menu bar registers on [os]. */
    private fun menuChords(os: DesktopOs): List<Accelerator> {
        val keys = DesktopShortcuts.map(os)
        return DesktopShortcuts.menuOwned.map { DesktopShortcuts.of(keys, it) } + DesktopShortcuts.menuOnly(os)
    }

    /** The chords the keyboard host runs: every chord of the key map that the host does not decline. */
    private fun hostChords(os: DesktopOs): List<Accelerator> {
        val meta = DesktopShortcuts.map(os).usesMetaKey
        val command = { key: Key, shift: Boolean -> Accelerator(key, ctrl = !meta, meta = meta, shift = shift) }
        return listOf(
            command(Key.F, false),
            command(Key.W, false),
            command(Key.Backspace, true),
            command(Key.PageDown, false),
            command(Key.PageUp, false),
            command(Key.RightBracket, true),
            command(Key.LeftBracket, true),
            Accelerator(Key.Tab, ctrl = true),
            Accelerator(Key.Tab, ctrl = true, shift = true),
            Accelerator(Key.Escape),
        )
    }

    @Test
    fun noChordTheMenuRegistersIsOneTheHostRuns() {
        for (os in DesktopOs.entries) {
            val keys = DesktopShortcuts.map(os)
            for (chord in menuChords(os)) {
                val command = keys.commandFor(chord.asEvent())
                assertTrue(
                    command == null || command in DesktopShortcuts.menuOwned,
                    "$os $chord is registered by the menu and would also run $command on the host",
                )
            }
        }
    }

    @Test
    fun noChordTheHostRunsIsOneTheMenuRegisters() {
        for (os in DesktopOs.entries) {
            val keys = DesktopShortcuts.map(os)
            val registered = menuChords(os)
            for (chord in hostChords(os)) {
                val command = keys.commandFor(chord.asEvent())
                assertTrue(command != null && command !in DesktopShortcuts.menuOwned, "$os $chord is not the host's")
                assertFalse(chord in registered, "$os $chord is also registered by the menu")
            }
        }
    }

    @Test
    fun everyCommandIsOwnedByExactlyOneSide() {
        val keys = DesktopShortcuts.map(DesktopOs.LINUX)
        for (command in AppCommand.entries) {
            val menu = command in DesktopShortcuts.menuOwned
            // A menu-owned command has an accelerator the menu can build from the map's own key.
            if (menu) assertTrue(keys.keyOf(command) != null, "$command has no key for the menu")
        }
        assertEquals(setOf(AppCommand.NewNote, AppCommand.LockApp), DesktopShortcuts.menuOwned)
    }

    @Test
    fun theMenuAcceleratorsAreTheKeyMapsOwn() {
        for (os in DesktopOs.entries) {
            val keys = DesktopShortcuts.map(os)
            DesktopShortcuts.menuOwned.forEach { command ->
                val accelerator = DesktopShortcuts.of(keys, command)
                assertEquals(keys.keyOf(command), accelerator.key)
                assertEquals(keys.usesMetaKey, accelerator.meta)
                assertEquals(!keys.usesMetaKey, accelerator.ctrl)
                assertFalse(accelerator.shift || accelerator.alt)
                // The label the user reads names the same chord.
                assertTrue(keys.label(command).endsWith(accelerator.key.letter()), keys.label(command))
            }
        }
    }

    @Test
    fun theMenuOnlyChordsAreUnknownToTheKeyMap() {
        for (os in DesktopOs.entries) {
            val keys = DesktopShortcuts.map(os)
            DesktopShortcuts.menuOnly(os).forEach { chord ->
                assertNull(keys.commandFor(chord.asEvent()), "$os $chord")
            }
        }
    }

    private fun Key.letter() = when (this) {
        Key.N -> "N"
        Key.L -> "L"
        else -> error("not a menu key")
    }

    @Test
    fun onTheDesktopWindowTheMenuChordsReachTheMenuAndTheHostChordsAreUsedUp() = runComposeUiTest {
        val seenAbove = mutableListOf<Key>()
        val asked = mutableListOf<AppCommand>()
        setContent {
            // What the window's native menu is: something outside the content that gets the keys the host leaves.
            Box(
                Modifier.onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) seenAbove.add(event.key)
                    false
                },
            ) {
                EnclyShortcutHost(
                    onCommand = { command ->
                        asked += command
                        command !in DesktopShortcuts.menuOwned
                    },
                    shortcuts = EnclyShortcuts(commandKeyIsMeta = false),
                ) {
                    Box(Modifier.size(10.dp))
                }
            }
        }

        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.N)
            pressKey(Key.L)
            pressKey(Key.W)
            pressKey(Key.I)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()

        assertEquals(listOf(AppCommand.NewNote, AppCommand.LockApp, AppCommand.CloseTab), asked)
        // N and L were declined and reached the menu; W was the host's; I was never a command.
        assertTrue(Key.N in seenAbove && Key.L in seenAbove && Key.I in seenAbove)
        assertFalse(Key.W in seenAbove)
    }
}
