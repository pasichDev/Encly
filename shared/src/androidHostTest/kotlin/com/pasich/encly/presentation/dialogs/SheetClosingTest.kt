package com.pasich.encly.presentation.dialogs

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pasich.encly.presentation.navigation.LocalVaultLocked
import com.pasich.encly.ui.screens.ComposeScreenTest
import com.pasich.encly.ui.theme.LocalWidthClass
import com.pasich.encly.ui.theme.WidthClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a sheet leaves: it slides away before it is taken out of the composition, and a row that closes it
 * runs its action only after that ([rememberSheetCloser], [SheetUntilHidden]).
 */
@OptIn(ExperimentalMaterial3Api::class)
class SheetClosingTest : ComposeScreenTest() {
    private val log = mutableListOf<String>()
    private var visible by mutableStateOf(true)
    private var locked by mutableStateOf(false)
    private lateinit var sheetState: SheetState

    /** A sheet with one row that closes it and then runs its action, twice over as a double tap would. */
    private fun showClosingSheet(width: WidthClass = WidthClass.Compact) {
        setScreen {
            CompositionLocalProvider(LocalWidthClass provides width) {
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val close = rememberSheetCloser(sheetState) {
                    log += "dismissed"
                    visible = false
                }
                if (visible) {
                    AdaptiveSheet(onDismissRequest = { visible = false }, sheetState = sheetState) {
                        TextButton(onClick = {
                            close { log += "action" }
                            close { log += "action" }
                        }) { Text("Row") }
                    }
                }
            }
        }
    }

    @Test
    fun aClosingRowRunsItsActionOnlyOnceTheSheetHasSlidAway() {
        showClosingSheet()
        rule.mainClock.autoAdvance = false

        rule.onNodeWithText("Row").performClick()
        aFewFrames()

        assertEquals("nothing runs while the sheet is still there", emptyList<String>(), log)
        assertEquals("the sheet is still composed, sliding away", 1, countText("Row"))

        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertEquals("dismissed first, then the action, once for a double tap", listOf("dismissed", "action"), log)
        assertEquals(0, countText("Row"))
    }

    @Test
    fun aWideWindowsDialogHasNothingToSlideAndClosesAtOnce() {
        showClosingSheet(WidthClass.Expanded)
        rule.mainClock.autoAdvance = false

        rule.onNodeWithText("Row").performClick()

        assertEquals("dismissed", log.first())
        assertTrue("action" in log)
    }

    /** Long enough to take a sheet out of the composition (one frame does), far too short for it to slide away. */
    private fun aFewFrames() {
        rule.waitForIdle()
        repeat(2) { rule.mainClock.advanceTimeByFrame() }
    }

    private fun showSheetUntilHidden() {
        setScreen {
            CompositionLocalProvider(LocalVaultLocked provides locked) {
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                SheetUntilHidden(visible = visible, sheetState = sheetState) {
                    AdaptiveSheet(onDismissRequest = { visible = false }, sheetState = sheetState) { Text("Content") }
                }
            }
        }
    }

    @Test
    fun aSheetClosedFromOutsideStaysUntilItHasSlidAway() {
        showSheetUntilHidden()
        rule.mainClock.autoAdvance = false

        visible = false
        aFewFrames()
        assertEquals("still composed while it slides away", 1, countText("Content"))

        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(0, countText("Content"))
    }

    @Test
    fun aSheetOpenedAgainIsShownAgain() {
        showSheetUntilHidden()
        visible = false
        rule.waitForIdle()

        visible = true
        rule.waitForIdle()

        assertEquals(1, countText("Content"))
        assertTrue(rule.runOnIdle { sheetState.isVisible })
    }

    @Test
    fun aLockedVaultKeepsNoClosedSheetOnDisplay() {
        showSheetUntilHidden()
        rule.mainClock.autoAdvance = false

        locked = true
        visible = false
        aFewFrames()

        assertEquals("gone as the vault locked, with no slide", 0, countText("Content"))
    }
}
