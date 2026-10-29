package com.pasich.encly.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.pin_delete_digit
import com.pasich.encly.core.resources.pin_digits_entered
import com.pasich.encly.presentation.designsystem.PIN_LENGTH
import com.pasich.encly.presentation.designsystem.PinKeypad
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.LockScreen
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.`when`

class PinKeypadTest : ComposeScreenTest() {

    private fun delete() = rule.onNodeWithContentDescription(str(Res.string.pin_delete_digit))

    @Test
    fun aTapOnDeleteRemovesOneDigit() {
        var deleted = 0
        setScreen { PinKeypad(onDigit = {}, onBackspace = { deleted++ }) }

        delete().performClick()

        assertEquals(1, deleted)
    }

    @Test
    fun aLongPressOnDeleteClearsEveryDigitThroughTheClearAction() {
        var deleted = 0
        var cleared = 0
        setScreen { PinKeypad(onDigit = {}, onBackspace = { deleted++ }, onClear = { cleared++ }) }

        delete().performTouchInput { longClick() }

        assertEquals(1, cleared)
        assertEquals(0, deleted)
    }

    @Test
    fun withoutAClearActionALongPressDeletesAWholePin() {
        var deleted = 0
        setScreen { PinKeypad(onDigit = {}, onBackspace = { deleted++ }) }

        delete().performTouchInput { longClick() }

        assertEquals(PIN_LENGTH, deleted)
    }

    @Test
    fun aDisabledKeypadIgnoresALongPress() {
        var deleted = 0
        setScreen { PinKeypad(onDigit = {}, onBackspace = { deleted++ }, enabled = false) }

        delete().performTouchInput { longClick() }

        assertEquals(0, deleted)
    }

    @Test
    fun onTheLockScreenALongPressOnDeleteEmptiesTheDots() {
        val app = TestApp()
        `when`(app.security.hasRecoverySeed()).thenReturn(true)
        setNavScreen(viewModels(app.lock()), route = NavRoutes.LockRoute.name) { nav -> LockScreen(nav) }
        typePin("123")
        rule.onNodeWithContentDescription(str(Res.string.pin_digits_entered, 3, PIN_LENGTH)).assertIsDisplayed()

        delete().performTouchInput { longClick() }

        rule.waitForIdle()
        rule.onNodeWithContentDescription(str(Res.string.pin_digits_entered, 0, PIN_LENGTH)).assertIsDisplayed()
    }
}
