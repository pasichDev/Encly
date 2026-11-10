package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** The middle click that closes a tab and the command-key click that opens a note in a new tab. */
@OptIn(ExperimentalTestApi::class)
class PointerClicksTest {
    private var clicks = 0
    private var middle = 0
    private var command = 0

    private fun ComposeUiTest.show(meta: Boolean = false) = setContent {
        CompositionLocalProvider(LocalEnclyShortcuts provides EnclyShortcuts(commandKeyIsMeta = meta)) {
            Box(
                Modifier
                    .testTag("target")
                    .size(40.dp)
                    .enclyMiddleClick { middle++ }
                    .enclyCommandClick { command++ }
                    .clickable { clicks++ },
            )
        }
    }

    @Test
    fun aMiddleClickCallsItAndLeavesTheClickAlone() = runComposeUiTest {
        show()

        onNodeWithTag("target").performMouseInput { middleClick() }

        assertEquals(1, middle)
        assertEquals(0, clicks)
        assertEquals(0, command)
    }

    @Test
    fun aMiddleClickReleasedOutsideDoesNothing() = runComposeUiTest {
        show()

        onNodeWithTag("target").performMouseInput {
            moveTo(center)
            press(MouseButton.Tertiary)
            moveTo(androidx.compose.ui.geometry.Offset(-100f, -100f))
            release(MouseButton.Tertiary)
        }

        assertEquals(0, middle)
    }

    @Test
    fun anOrdinaryClickIsJustAClick() = runComposeUiTest {
        show()

        onNodeWithTag("target").performMouseInput { click() }

        assertEquals(1, clicks)
        assertEquals(0, middle)
        assertEquals(0, command)
    }

    @Test
    fun aRightClickIsNeitherOfThem() = runComposeUiTest {
        show()

        onNodeWithTag("target").performMouseInput { rightClick() }

        assertEquals(0, middle)
        assertEquals(0, command)
    }

    @Test
    fun aClickWithControlHeldIsTheCommandClickOffTheMac() = runComposeUiTest {
        show(meta = false)

        onNodeWithTag("target").performKeyInput { keyDown(Key.CtrlLeft) }
        onNodeWithTag("target").performMouseInput { click() }
        onNodeWithTag("target").performKeyInput { keyUp(Key.CtrlLeft) }

        assertEquals(1, command)
        assertEquals(0, clicks)
    }

    @Test
    fun aClickWithCommandHeldIsTheCommandClickOnAMac() = runComposeUiTest {
        show(meta = true)

        onNodeWithTag("target").performKeyInput { keyDown(Key.MetaLeft) }
        onNodeWithTag("target").performMouseInput { click() }
        onNodeWithTag("target").performKeyInput { keyUp(Key.MetaLeft) }

        assertEquals(1, command)
        assertEquals(0, clicks)
    }

    @Test
    fun theWrongCommandKeyLeavesTheClickAlone() = runComposeUiTest {
        show(meta = true)

        onNodeWithTag("target").performKeyInput { keyDown(Key.CtrlLeft) }
        onNodeWithTag("target").performMouseInput { click() }
        onNodeWithTag("target").performKeyInput { keyUp(Key.CtrlLeft) }

        assertEquals(0, command)
        assertEquals(1, clicks)
    }
}
