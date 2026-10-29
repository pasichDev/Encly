package com.pasich.encly.presentation.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The card of the note that is open beside the list, and the click that opens a note in a new tab. */
@OptIn(ExperimentalTestApi::class)
class NoteCardCurrentTest {
    private var clicks = 0
    private var commandClicks = 0

    private fun androidx.compose.ui.test.ComposeUiTest.show(current: Boolean, withCommandClick: Boolean = true) =
        setContent {
            CompositionLocalProvider(LocalEnclyShortcuts provides EnclyShortcuts(commandKeyIsMeta = false)) {
                MaterialTheme {
                    EnclyNoteCard(
                        title = "Trip",
                        excerpt = "Pack the bags",
                        onClick = { clicks++ },
                        current = current,
                        onCommandClick = if (withCommandClick) ({ commandClicks++ }) else null,
                    )
                }
            }
        }

    @Test
    fun theCardOfTheShownNoteIsMarkedSelectedForAssistiveTechnology() = runComposeUiTest {
        show(current = true)

        onNode(hasClickAction()).assertIsSelected()
    }

    @Test
    fun anotherCardIsNot() = runComposeUiTest {
        show(current = false)

        onNode(hasClickAction()).assertIsNotSelected()
    }

    @Test
    fun anOrdinaryClickOpensTheNote() = runComposeUiTest {
        show(current = false)

        onNode(hasClickAction()).performMouseInput { click() }

        assertEquals(1, clicks)
        assertEquals(0, commandClicks)
    }

    @Test
    fun aClickWithTheCommandKeyOpensItInANewTabInstead() = runComposeUiTest {
        show(current = false)

        onNode(hasClickAction()).performKeyInput { keyDown(Key.CtrlLeft) }
        onNode(hasClickAction()).performMouseInput { click() }
        onNode(hasClickAction()).performKeyInput { keyUp(Key.CtrlLeft) }

        assertEquals(0, clicks)
        assertEquals(1, commandClicks)
    }

    @Test
    fun withoutANewTabHandlerTheCommandClickIsAnOrdinaryClick() = runComposeUiTest {
        show(current = false, withCommandClick = false)

        onNode(hasClickAction()).performKeyInput { keyDown(Key.CtrlLeft) }
        onNode(hasClickAction()).performMouseInput { click() }
        onNode(hasClickAction()).performKeyInput { keyUp(Key.CtrlLeft) }

        assertEquals(1, clicks)
    }
}
