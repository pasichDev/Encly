package com.pasich.encly.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.lock_recover_access
import com.pasich.encly.core.resources.lock_subtitle
import com.pasich.encly.core.resources.lock_unlock
import com.pasich.encly.core.resources.lock_use_recovery_phrase
import com.pasich.encly.core.resources.pin_digits_count
import com.pasich.encly.core.resources.recovery_choice_body_pin
import com.pasich.encly.core.resources.recovery_keep_pin
import com.pasich.encly.core.resources.recovery_set_new_pin
import com.pasich.encly.core.security.RecoveryStage
import com.pasich.encly.core.security.VaultUnlockResult
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.LockScreen
import com.pasich.encly.presentation.screen.RecoveryChoiceScreen
import com.pasich.encly.testutil.anyCharArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * The PIN of 6 to 12 digits and the choice after the recovery phrase. The PIN screens never submit by themselves;
 * a button does.
 */
@OptIn(ExperimentalTestApi::class)
class PinLengthScreensTest : ComposeScreenTest() {
    private val app by lazy { TestApp() }

    private fun showLock() {
        `when`(app.security.hasRecoverySeed()).thenReturn(true)
        setNavScreen(viewModels(app.lock()), route = NavRoutes.LockRoute.name) { nav -> LockScreen(nav) }
    }

    private fun anyChars(): CharArray = org.mockito.ArgumentMatchers.any(CharArray::class.java) ?: CharArray(0)

    // --- PIN length on the lock screen ---------------------------------------------------------

    @Test
    fun theUnlockButtonWaitsForSixDigitsAndNothingIsSentByItself() {
        `when`(app.security.unlockWithPin(anyCharArray())).thenReturn(VaultUnlockResult.SUCCESS)
        showLock()
        rule.onNodeWithText(str(Res.string.lock_unlock)).assertIsNotEnabled()

        typePin("12345")
        rule.onNodeWithText(str(Res.string.lock_unlock)).assertIsNotEnabled()

        typePin("6")
        rule.onNodeWithText(str(Res.string.lock_unlock)).assertIsEnabled()
        rule.waitForIdle()
        verify(app.security, never()).unlockWithPin(anyCharArray())
        assertEquals(NavRoutes.LockRoute.name, currentRoute())
    }

    @Test
    fun aPinOfEightDigitsIsSentWhole() {
        var sent = ""
        `when`(app.security.unlockWithPin(anyCharArray())).thenAnswer {
            sent = String(it.getArgument<CharArray>(0))
            VaultUnlockResult.SUCCESS
        }
        showLock()

        enterPin("12345678")

        waitFor { currentRoute() == NavRoutes.HomeRoute.name }
        assertEquals("12345678", sent)
    }

    @Test
    fun theDotsGrowPastSixAndStopAtTwelve() {
        showLock()

        typePin("1234567")
        rule.onNodeWithContentDescription(str(Res.string.pin_digits_count, 7)).assertExists()

        typePin("890123456")

        rule.onNodeWithContentDescription(str(Res.string.pin_digits_count, 12)).assertExists()
    }

    @Test
    fun theSubtitleDoesNotSayHowLongThePinIs() {
        showLock()

        rule.onNodeWithText(str(Res.string.lock_subtitle)).assertIsDisplayed()
        assertTrue(str(Res.string.lock_subtitle).none(Char::isDigit))
    }

    // --- after the recovery phrase ---------------------------------------------------------------------

    @Test
    fun aWorkingPinOffersTheChoiceAfterTheRecoveryPhrase() {
        `when`(app.security.unlockWithSeed(anyChars())).thenReturn(VaultUnlockResult.SUCCESS)
        `when`(app.security.recoveryStage()).thenReturn(RecoveryStage.CHOOSE)
        showLock()
        rule.onNodeWithText(str(Res.string.lock_use_recovery_phrase)).performClick()
        phraseCell(1).performTextReplacement(PHRASE)

        rule.onNodeWithText(str(Res.string.lock_recover_access)).performClick()

        waitFor { currentRoute() == NavRoutes.RecoveryChoiceRoute.name }
    }

    @Test
    fun keepingThePinGoesStraightIntoTheApp() {
        `when`(app.security.recoveryStage()).thenReturn(RecoveryStage.CHOOSE)
        `when`(app.security.keepUnlockMethodsAfterRecovery()).thenReturn(true)
        setNavScreen(
            viewModels(app.lock()),
            route = NavRoutes.RecoveryChoiceRoute.name,
        ) { nav -> RecoveryChoiceScreen(nav) }

        rule.onNodeWithText(str(Res.string.recovery_choice_body_pin)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.recovery_set_new_pin)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.recovery_keep_pin)).performClick()

        waitFor { currentRoute() == NavRoutes.HomeRoute.name }
        verify(app.security).keepUnlockMethodsAfterRecovery()
    }

    @Test
    fun choosingANewPinOpensThePinResetWithoutKeeping() {
        `when`(app.security.recoveryStage()).thenReturn(RecoveryStage.CHOOSE)
        setNavScreen(
            viewModels(app.lock()),
            route = NavRoutes.RecoveryChoiceRoute.name,
        ) { nav -> RecoveryChoiceScreen(nav) }

        rule.onNodeWithText(str(Res.string.recovery_set_new_pin)).performClick()

        waitFor { currentRoute() == NavRoutes.PinCodeConfig.name }
        verify(app.security, never()).keepUnlockMethodsAfterRecovery()
    }
}
