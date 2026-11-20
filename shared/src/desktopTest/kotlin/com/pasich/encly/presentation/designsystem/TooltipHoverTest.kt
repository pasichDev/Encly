package com.pasich.encly.presentation.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** On a desktop an icon button names itself when the pointer rests on it, and a click still acts. */
@OptIn(ExperimentalTestApi::class)
class TooltipHoverTest {
    private var clicks = 0

    private fun androidx.compose.ui.test.ComposeUiTest.show() = setContent {
        MaterialTheme {
            IconWithTooltip(EnclyIcons.Undo, "Undo", onClick = { clicks++ }, modifier = Modifier.testTag("button"))
        }
    }

    @Test
    fun restingThePointerOnTheButtonShowsItsLabel() = runComposeUiTest {
        mainClock.autoAdvance = false
        show()
        onNodeWithText("Undo").assertDoesNotExist()

        onNodeWithTag("button").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(TOOLTIP_WAIT_MS)
        waitForIdle()

        onNodeWithText("Undo").assertExists()
    }

    @Test
    fun leavingTheButtonHidesItAgain() = runComposeUiTest {
        mainClock.autoAdvance = false
        show()
        onNodeWithTag("button").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(TOOLTIP_WAIT_MS)
        waitForIdle()

        onNodeWithTag("button").performMouseInput { exit() }
        mainClock.advanceTimeBy(TOOLTIP_WAIT_MS)
        waitForIdle()

        onNodeWithText("Undo").assertDoesNotExist()
    }

    @Test
    fun aClickStillRunsTheAction() = runComposeUiTest {
        show()

        onNodeWithTag("button").performMouseInput { click() }

        assertEquals(1, clicks)
    }

    private companion object {
        const val TOOLTIP_WAIT_MS = 3_000L
    }
}
