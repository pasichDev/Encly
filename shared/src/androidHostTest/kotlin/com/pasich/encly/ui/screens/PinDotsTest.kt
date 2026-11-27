package com.pasich.encly.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.pin_digits_count
import com.pasich.encly.presentation.designsystem.PIN_DOT_ERROR_TAG
import com.pasich.encly.presentation.designsystem.PIN_DOT_TAG
import com.pasich.encly.presentation.designsystem.PinDots
import com.pasich.encly.presentation.designsystem.pinFitFor
import com.pasich.encly.presentation.screen.pincode.PinEntry
import com.pasich.encly.presentation.screen.pincode.PinEntryActions
import com.pasich.encly.presentation.screen.pincode.PinEntryScaffold
import com.pasich.encly.presentation.screen.pincode.PinSubmit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinDotsTest : ComposeScreenTest() {

    @Test
    fun theRowHasTheDotHeightBeforeTheFirstDigit() {
        setScreen { PinDots(entered = 0) }

        rule.onNodeWithContentDescription(str(Res.string.pin_digits_count, 0)).assertHeightIsEqualTo(14.dp)
    }

    @Test
    fun theRowHasTheSameHeightOnceDigitsAreTyped() {
        setScreen { PinDots(entered = 3) }

        rule.onNodeWithContentDescription(str(Res.string.pin_digits_count, 3)).assertHeightIsEqualTo(14.dp)
    }

    @Test
    fun aNormalPhoneKeepsTheFullLayoutAndAShortWindowShrinks() {
        assertEquals(1f, pinFitFor(800.dp).fraction)
        assertTrue(pinFitFor(800.dp).showHeaderIcon)
        assertTrue(pinFitFor(600.dp).fraction in 0f..1f && pinFitFor(600.dp).fraction < 1f)
        assertEquals(0f, pinFitFor(420.dp).fraction)
        assertFalse(pinFitFor(420.dp).showHeaderIcon)
    }

    @Test
    fun theWholeKeypadAndTheUnlockActionFitAShortWindow() {
        setScreen {
            Box(Modifier.height(420.dp)) {
                PinEntryScaffold(title = "Title", subtitle = "Subtitle", bodyAtBottom = true) { fit ->
                    PinEntry(
                        fit = fit,
                        entered = 0,
                        actions = PinEntryActions(onDigit = {}, onBackspace = {}),
                        submit = PinSubmit(text = "Go", enabled = true, onClick = {}),
                    )
                }
            }
        }

        rule.onNodeWithText("0").assertIsDisplayed()
        rule.onNodeWithText("Go").assertIsDisplayed()
    }

    @Test
    fun aFailedAttemptKeepsItsDotsInTheErrorColourUntilTheNextDigit() {
        var entered by mutableIntStateOf(8)
        var error by mutableStateOf(false)
        setScreen { PinDots(entered = entered, error = error) }

        // The attempt is checked and fails: the digits are cleared and the error is shown.
        entered = 0
        error = true
        rule.waitForIdle()

        rule.onAllNodesWithTag(PIN_DOT_ERROR_TAG).assertCountEquals(8)
        rule.onAllNodesWithTag(PIN_DOT_TAG).assertCountEquals(0)

        // The first digit of the next attempt clears the error.
        entered = 1
        error = false
        rule.waitForIdle()

        rule.onAllNodesWithTag(PIN_DOT_TAG).assertCountEquals(1)
        rule.onAllNodesWithTag(PIN_DOT_ERROR_TAG).assertCountEquals(0)
    }

    @Test
    fun anErrorWithNoFailedAttemptShowsNoDots() {
        setScreen { PinDots(entered = 0, error = true) }

        rule.onAllNodesWithTag(PIN_DOT_ERROR_TAG).assertCountEquals(0)
    }
}
