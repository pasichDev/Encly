package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The map is one table: its keys, its labels and the window's copy of it all say the same. */
@OptIn(ExperimentalTestApi::class)
class EnclyShortcutsMapTest {
    @Test
    fun theLetterCommandsHaveTheirKeysAndNoOtherCommandDoes() {
        val shortcuts = EnclyShortcuts(commandKeyIsMeta = false)

        assertEquals(Key.N, shortcuts.keyOf(AppCommand.NewNote))
        assertEquals(Key.F, shortcuts.keyOf(AppCommand.FindInNote))
        assertEquals(Key.L, shortcuts.keyOf(AppCommand.LockApp))
        assertEquals(Key.W, shortcuts.keyOf(AppCommand.CloseTab))
        listOf(AppCommand.NextTab, AppCommand.PreviousTab, AppCommand.DeleteNote, AppCommand.Dismiss).forEach {
            assertNull(shortcuts.keyOf(it), "$it")
        }
    }

    @Test
    fun theLabelOfALetterCommandNamesItsKey() {
        val letters = mapOf(
            AppCommand.NewNote to Key.N,
            AppCommand.FindInNote to Key.F,
            AppCommand.LockApp to Key.L,
            AppCommand.CloseTab to Key.W,
        )
        for (meta in listOf(true, false)) {
            val shortcuts = EnclyShortcuts(commandKeyIsMeta = meta)
            letters.forEach { (command, key) ->
                assertEquals(key, shortcuts.keyOf(command))
                assertTrue(shortcuts.label(command).endsWith(command.letter()), "${shortcuts.label(command)}")
            }
        }
    }

    private fun AppCommand.letter() = when (this) {
        AppCommand.NewNote -> "N"
        AppCommand.FindInNote -> "F"
        AppCommand.LockApp -> "L"
        else -> "W"
    }

    @Test
    fun theMapKnowsWhichCommandKeyItWasMadeFor() {
        assertTrue(EnclyShortcuts(commandKeyIsMeta = true).usesMetaKey)
        assertFalse(EnclyShortcuts(commandKeyIsMeta = false).usesMetaKey)
    }

    @Test
    fun theHostGivesItsMapToEverythingUnderIt() = runComposeUiTest {
        setContent {
            EnclyShortcutHost(onCommand = { false }, shortcuts = EnclyShortcuts(commandKeyIsMeta = true)) {
                Text(LocalEnclyShortcuts.current.label(AppCommand.NewNote))
            }
        }

        onNodeWithText("⌘N").assertExists()
    }

    private fun keyDown(event: KeyEvent): Key? = event.key.takeIf { event.type == KeyEventType.KeyDown }

    @Test
    fun aCommandTheHostDeclinesReachesWhoeverIsAboveIt() = runComposeUiTest {
        val seenAbove = mutableListOf<Key>()
        val asked = mutableListOf<AppCommand>()
        setContent {
            Box(Modifier.onKeyEvent { event: KeyEvent -> keyDown(event)?.let(seenAbove::add).let { false } }) {
                EnclyShortcutHost(
                    onCommand = { command ->
                        asked += command
                        command != AppCommand.NewNote
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
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()

        assertEquals(listOf(AppCommand.NewNote, AppCommand.LockApp), asked)
        // The declined chord went on up; the handled one was used up.
        assertTrue(Key.N in seenAbove)
        assertFalse(Key.L in seenAbove)
    }
}
