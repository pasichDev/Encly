package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The right-click menu: it opens at the pointer, runs the entry picked and leaves a finger's tap alone. */
@OptIn(ExperimentalTestApi::class)
class ContextMenuTest {
    private var clicks = 0
    private var built = 0
    private val ran = mutableListOf<String>()

    private fun ComposeUiTest.show(
        enabled: Boolean = true,
        claimPress: Boolean = true,
        child: Modifier = Modifier,
        clickable: Boolean = true,
    ) = setContent {
        MaterialTheme {
            EnclyContextMenu(
                entries = {
                    built++
                    listOf(
                        MenuEntry("Open", { ran += "open" }, EnclyIcons.Edit),
                        MenuEntry("Remove", { ran += "remove" }, destructive = true),
                        MenuEntry("Locked", { ran += "locked" }, enabled = false),
                    )
                },
                enabled = enabled,
                claimPress = claimPress,
            ) {
                Box(
                    Modifier
                        .testTag("card")
                        .size(CARD)
                        .then(child)
                        .then(if (clickable) Modifier.clickable { clicks++ } else Modifier.focusable()),
                )
            }
        }
    }

    @Test
    fun aRightClickOpensTheMenuWithoutClickingTheCard() = runComposeUiTest {
        show()

        onNodeWithTag("card").performMouseInput { rightClick() }
        waitForIdle()

        onNodeWithText("Open").assertExists()
        onNodeWithText("Remove").assertExists()
        onNodeWithText("Locked").assertIsNotEnabled()
        assertEquals(0, clicks)
        assertEquals(1, built)
    }

    @Test
    fun pickingAnEntryRunsItAndClosesTheMenu() = runComposeUiTest {
        show()
        onNodeWithTag("card").performMouseInput { rightClick() }
        waitForIdle()

        onNodeWithText("Remove").performClick()
        waitForIdle()

        assertEquals(listOf("remove"), ran)
        onNodeWithText("Remove").assertDoesNotExist()
    }

    @Test
    fun aDisabledEntryDoesNothing() = runComposeUiTest {
        show()
        onNodeWithTag("card").performMouseInput { rightClick() }
        waitForIdle()

        onNodeWithText("Locked").performClick()
        waitForIdle()

        assertTrue(ran.isEmpty())
    }

    @Test
    fun aLeftClickJustClicksTheCard() = runComposeUiTest {
        show()

        onNodeWithTag("card").performMouseInput { click() }
        waitForIdle()

        assertEquals(1, clicks)
        onNodeWithText("Open").assertDoesNotExist()
        assertEquals(0, built)
    }

    @Test
    fun escapeClosesTheMenu() = runComposeUiTest {
        show()
        onNodeWithTag("card").performMouseInput { rightClick() }
        waitForIdle()

        onNodeWithText("Open").requestFocus()
        onNodeWithText("Open").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()

        onNodeWithText("Open").assertDoesNotExist()
        assertTrue(ran.isEmpty())
    }

    @Test
    fun aDisabledMenuLeavesTheContentAlone() = runComposeUiTest {
        show(enabled = false)

        onNodeWithTag("card").performMouseInput { rightClick() }
        waitForIdle()

        onNodeWithText("Open").assertDoesNotExist()
        assertEquals(0, built)
    }

    @Test
    fun theMenuKeyAndShiftF10OpenItForTheFocusedContent() = runComposeUiTest {
        show()
        onNodeWithTag("card").requestFocus()
        onNodeWithTag("card").performKeyInput { pressKey(Key.Menu) }
        waitForIdle()
        onNodeWithText("Open").assertExists()
        onNodeWithText("Open").performClick()
        waitForIdle()
        assertEquals(listOf("open"), ran)

        onNodeWithTag("card").requestFocus()
        onNodeWithTag("card").performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.F10) } }
        waitForIdle()
        onNodeWithText("Remove").assertExists()
    }

    @Test
    fun otherKeysOpenNothing() = runComposeUiTest {
        show()
        onNodeWithTag("card").requestFocus()

        onNodeWithTag("card").performKeyInput { pressKey(Key.F10) }
        onNodeWithTag("card").performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.F9) } }
        waitForIdle()

        onNodeWithText("Open").assertDoesNotExist()
    }

    @Test
    fun withoutClaimingAChildThatUsedTheRightClickKeepsIt() = runComposeUiTest {
        show(claimPress = false, child = Modifier.pointerInput(Unit) { consumeEverything() })

        onNodeWithTag("card").performMouseInput { rightClick() }
        waitForIdle()

        onNodeWithText("Open").assertDoesNotExist()
    }

    @Test
    fun withoutClaimingARightClickNobodyUsedStillOpensIt() = runComposeUiTest {
        show(claimPress = false, clickable = false)

        onNodeWithTag("card").performMouseInput { rightClick() }
        waitForIdle()

        onNodeWithText("Open").assertExists()
    }

    private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.consumeEverything() {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }

    private companion object {
        val CARD = 100.dp
    }
}
