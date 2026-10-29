package com.pasich.encly.presentation.pane

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.pasich.encly.presentation.designsystem.EnclyShortcuts
import com.pasich.encly.presentation.designsystem.LocalEnclyShortcuts
import com.pasich.encly.presentation.designsystem.middleClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The strip of open notes with a mouse: select, middle-click to close, the hint on the close button. */
@OptIn(ExperimentalTestApi::class)
class NoteTabStripTest {
    private val selected = mutableListOf<Long>()
    private val closed = mutableListOf<Long>()
    private val tabs = listOf(
        NoteTab(key = 1, noteId = 11, title = "Trip"),
        NoteTab(key = 2, noteId = 12, title = "Groceries"),
        NoteTab(key = 3, noteId = 13, title = ""),
    )

    private fun ComposeUiTest.show(width: Int = 800, activeKey: Long = 1) = setContent {
        CompositionLocalProvider(LocalEnclyShortcuts provides EnclyShortcuts(commandKeyIsMeta = false)) {
            MaterialTheme {
                Box(Modifier.width(width.dp)) {
                    NoteTabStrip(tabs, activeKey, onSelect = { selected += it }, onClose = { closed += it })
                }
            }
        }
    }

    private fun ComposeUiTest.closeButton(name: String) =
        onNodeWithContentDescription("Close $name", useUnmergedTree = true)

    @Test
    fun clickingATabSelectsIt() = runComposeUiTest {
        show()

        onNodeWithText("Groceries").performMouseInput { click() }

        assertEquals(listOf(2L), selected)
    }

    @Test
    fun aMiddleClickClosesATabWithoutSelectingIt() = runComposeUiTest {
        show()

        onNodeWithText("Groceries").performMouseInput { middleClick() }

        assertEquals(listOf(2L), closed)
        assertTrue(selected.isEmpty())
    }

    @Test
    fun theCloseButtonClosesItsOwnTab() = runComposeUiTest {
        show()

        closeButton("Groceries").performMouseInput { click() }

        assertEquals(listOf(2L), closed)
        assertTrue(selected.isEmpty())
    }

    @Test
    fun theShownTabIsMarked() = runComposeUiTest {
        show(activeKey = 2)

        onAllNodesWithTag(NOTE_TAB_TAG)[1].assertIsSelected()
    }

    @Test
    fun hoveringTheShownTabsCloseButtonNamesTheKeyThatClosesIt() = runComposeUiTest {
        mainClock.autoAdvance = false
        show()

        closeButton("Trip").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(TOOLTIP_WAIT_MS)
        waitForIdle()

        onNodeWithText("Close Trip (Ctrl+W)").assertExists()
    }

    @Test
    fun aTabThatIsNotShownHasNoKeyInItsHint() = runComposeUiTest {
        mainClock.autoAdvance = false
        show()

        closeButton("Groceries").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(TOOLTIP_WAIT_MS)
        waitForIdle()

        onNodeWithText("Close Groceries").assertExists()
    }

    @Test
    fun aBlankTabIsCalledUntitled() = runComposeUiTest {
        show()

        assertEquals(3, onAllNodesWithTag(NOTE_TAB_TAG).fetchSemanticsNodes().size)
        closeButton("Untitled").assertExists()
    }

    @Test
    fun theTabsStayReachableWhenTheyDoNotFit() = runComposeUiTest {
        show(width = 160)

        assertEquals(3, onAllNodesWithTag(NOTE_TAB_TAG).fetchSemanticsNodes().size)
        onNodeWithText("Trip").assertExists()
    }

    private companion object {
        const val TOOLTIP_WAIT_MS = 3_000L
    }
}
