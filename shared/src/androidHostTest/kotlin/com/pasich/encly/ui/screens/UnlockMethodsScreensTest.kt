package com.pasich.encly.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.action_continue
import com.pasich.encly.core.resources.lock_password_subtitle
import com.pasich.encly.core.resources.lock_recover_access
import com.pasich.encly.core.resources.lock_subtitle
import com.pasich.encly.core.resources.lock_unlock
import com.pasich.encly.core.resources.lock_use_password
import com.pasich.encly.core.resources.lock_use_pin
import com.pasich.encly.core.resources.lock_use_recovery_phrase
import com.pasich.encly.core.resources.lock_wrong_password
import com.pasich.encly.core.resources.password_change
import com.pasich.encly.core.resources.password_current_wrong
import com.pasich.encly.core.resources.password_failed
import com.pasich.encly.core.resources.password_mismatch
import com.pasich.encly.core.resources.password_pin_only_note
import com.pasich.encly.core.resources.password_remove
import com.pasich.encly.core.resources.password_remove_confirm
import com.pasich.encly.core.resources.password_removed
import com.pasich.encly.core.resources.password_save
import com.pasich.encly.core.resources.password_saved
import com.pasich.encly.core.resources.password_set
import com.pasich.encly.core.resources.password_too_short
import com.pasich.encly.core.resources.pin_digits_count
import com.pasich.encly.core.resources.pin_remove
import com.pasich.encly.core.resources.pin_remove_confirm
import com.pasich.encly.core.resources.pin_removed
import com.pasich.encly.core.resources.recovery_choice_body_password
import com.pasich.encly.core.resources.recovery_choice_body_pin
import com.pasich.encly.core.resources.recovery_keep_password
import com.pasich.encly.core.resources.recovery_keep_pin
import com.pasich.encly.core.resources.recovery_set_new_pin
import com.pasich.encly.core.security.AuthSettings
import com.pasich.encly.core.security.AuthType
import com.pasich.encly.core.security.PasswordChange
import com.pasich.encly.core.security.RecoveryStage
import com.pasich.encly.core.security.UnlockMethods
import com.pasich.encly.core.security.VaultUnlockResult
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.LockScreen
import com.pasich.encly.presentation.screen.RecoveryChoiceScreen
import com.pasich.encly.presentation.screen.settings.PasswordScreen
import com.pasich.encly.testutil.anyCharArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * The PIN of 6 to 12 digits and the password as a second way in: the lock screen, the choice after the recovery
 * phrase and Settings → Security → Password. The PIN screens never submit by themselves; a button does.
 */
@OptIn(ExperimentalTestApi::class)
class UnlockMethodsScreensTest : ComposeScreenTest() {
    private val app by lazy { TestApp() }

    private fun showLock(methods: UnlockMethods = UnlockMethods(pin = true, password = false)) {
        `when`(app.security.unlockMethods()).thenReturn(methods)
        `when`(app.security.hasRecoverySeed()).thenReturn(true)
        setNavScreen(viewModels(app.lock()), route = NavRoutes.LockRoute.name) { nav -> LockScreen(nav) }
    }

    private fun anyChars(): CharArray = org.mockito.ArgumentMatchers.any(CharArray::class.java) ?: CharArray(0)

    private fun typePassword(text: String) {
        rule.onNode(hasSetTextAction()).performTextInput(text)
    }

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

    // --- the password on the lock screen -------------------------------------------------------------

    @Test
    fun withoutAPasswordThereIsNoSwitch() {
        showLock()

        assertEquals(0, countText(str(Res.string.lock_use_password)))
    }

    @Test
    fun aVaultWithBothOffersToSwitchBetweenThem() {
        showLock(UnlockMethods(pin = true, password = true))
        rule.onNodeWithText(str(Res.string.lock_use_password)).performClick()

        rule.onNodeWithText(str(Res.string.lock_password_subtitle)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.lock_use_pin)).performClick()

        rule.onNodeWithText(str(Res.string.lock_subtitle)).assertIsDisplayed()
    }

    @Test
    fun thePasswordUnlocksOnItsOwn() {
        var sent = ""
        `when`(app.security.unlockWithPassword(anyCharArray())).thenAnswer {
            sent = String(it.getArgument<CharArray>(0))
            VaultUnlockResult.SUCCESS
        }
        showLock(UnlockMethods(pin = true, password = true))
        rule.onNodeWithText(str(Res.string.lock_use_password)).performClick()
        rule.onNodeWithText(str(Res.string.lock_unlock)).assertIsNotEnabled()

        typePassword("correct horse")
        rule.onNodeWithText(str(Res.string.lock_unlock)).assertIsEnabled().performClick()

        waitFor { currentRoute() == NavRoutes.HomeRoute.name }
        assertEquals("correct horse", sent)
        verify(app.security, never()).unlockWithPin(anyCharArray())
    }

    @Test
    fun aShortPasswordCanNotBeSent() {
        showLock(UnlockMethods(pin = true, password = true))
        rule.onNodeWithText(str(Res.string.lock_use_password)).performClick()

        typePassword("1234567")

        rule.onNodeWithText(str(Res.string.lock_unlock)).assertIsNotEnabled()
        verify(app.security, never()).unlockWithPassword(anyCharArray())
    }

    @Test
    fun aWrongPasswordSaysSoAndStays() {
        `when`(app.security.unlockWithPassword(anyCharArray())).thenReturn(VaultUnlockResult.INVALID_CREDENTIAL)
        showLock(UnlockMethods(pin = true, password = true))
        rule.onNodeWithText(str(Res.string.lock_use_password)).performClick()
        typePassword("not the password")

        rule.onNodeWithText(str(Res.string.lock_unlock)).performClick()

        waitForText(str(Res.string.lock_wrong_password))
        assertEquals(NavRoutes.LockRoute.name, currentRoute())
    }

    @Test
    fun aVaultWithOnlyAPasswordOpensOnTheFormWithoutAPinSwitch() {
        showLock(UnlockMethods(pin = false, password = true))

        rule.onNodeWithText(str(Res.string.lock_password_subtitle)).assertIsDisplayed()
        assertEquals(0, countText(str(Res.string.lock_use_pin)))
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

    @Test
    fun aVaultWithOnlyAPasswordKeepsItsPassword() {
        `when`(app.security.unlockMethods()).thenReturn(UnlockMethods(pin = false, password = true))
        `when`(app.security.recoveryStage()).thenReturn(RecoveryStage.CHOOSE)
        setNavScreen(
            viewModels(app.lock()),
            route = NavRoutes.RecoveryChoiceRoute.name,
        ) { nav -> RecoveryChoiceScreen(nav) }

        rule.onNodeWithText(str(Res.string.recovery_choice_body_password)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.recovery_keep_password)).assertIsDisplayed()
    }

    // --- Settings → Security → Password ----------------------------------------------------------------

    private fun showPassword(hasPassword: Boolean, hasPin: Boolean = true) {
        `when`(app.security.unlockMethods()).thenReturn(UnlockMethods(pin = hasPin, password = hasPassword))
        `when`(app.security.getSettingsAuth()).thenReturn(
            AuthSettings(AuthType.PIN, false, true, hasPinSlot = hasPin, hasPassword = hasPassword),
        )
        `when`(app.security.verifyPin(anyCharArray())).thenReturn(true)
        `when`(app.security.verifyPassword(anyCharArray())).thenReturn(true)
        setNavScreen(
            viewModels(app.securitySettings()),
            route = NavRoutes.PasswordRoute.name,
            start = NavRoutes.SecuritySettingsRoute.name,
        ) { nav -> PasswordScreen(nav) }
        navigateTo(NavRoutes.PasswordRoute.name)
    }

    @Test
    fun theWipeAndDuressPinsAreSaidToStayPinOnly() {
        showPassword(hasPassword = false)
        enterPin("123456")

        rule.onNodeWithText(str(Res.string.password_pin_only_note)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.password_set)).assertIsDisplayed()
    }

    @Test
    fun theOwnerIsAskedForThePinBeforeAnythingChanges() {
        showPassword(hasPassword = false)

        assertEquals(0, countText(str(Res.string.password_set)))
        enterPin("123456")

        verify(app.security).verifyPin(anyCharArray())
        rule.onNodeWithText(str(Res.string.password_set)).assertIsDisplayed()
    }

    @Test
    fun aVaultWithoutAPinIsAskedForItsPassword() {
        showPassword(hasPassword = true, hasPin = false)

        typePassword("correct horse")
        rule.onNodeWithText(str(Res.string.action_continue)).performClick()

        waitForText(str(Res.string.password_change))
        verify(app.security).verifyPassword(anyCharArray())
        verify(app.security, never()).verifyPin(anyCharArray())
    }

    @Test
    fun aNewPasswordIsTypedTwiceAndSaved() {
        var saved = ""
        `when`(app.security.changePassword(anyCharArray())).thenAnswer {
            saved = String(it.getArgument<CharArray>(0))
            PasswordChange.SET
        }
        showPassword(hasPassword = false)
        enterPin("123456")
        rule.onNodeWithText(str(Res.string.password_set)).performClick()

        val fields = rule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("a long enough one")
        fields[1].performTextInput("a long enough one")
        rule.onNodeWithText(str(Res.string.password_save)).assertIsEnabled().performClick()

        waitForText(str(Res.string.password_saved))
        assertEquals("a long enough one", saved)
    }

    @Test
    fun passwordsThatDoNotMatchAreNotSaved() {
        showPassword(hasPassword = false)
        enterPin("123456")
        rule.onNodeWithText(str(Res.string.password_set)).performClick()

        val fields = rule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("a long enough one")
        fields[1].performTextInput("a long enough two")
        rule.onNodeWithText(str(Res.string.password_save)).performClick()

        waitForText(str(Res.string.password_mismatch))
        verify(app.security, never()).changePassword(anyCharArray())
    }

    @Test
    fun aPasswordThatIsTooShortCanNotBeSaved() {
        showPassword(hasPassword = false)
        enterPin("123456")
        rule.onNodeWithText(str(Res.string.password_set)).performClick()

        val fields = rule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("short")
        fields[1].performTextInput("short")

        rule.onNodeWithText(str(Res.string.password_save)).assertIsNotEnabled()
    }

    @Test
    fun theCoreRefusingAPasswordIsShown() {
        `when`(app.security.changePassword(anyCharArray())).thenReturn(PasswordChange.INVALID)
        showPassword(hasPassword = false)
        enterPin("123456")
        rule.onNodeWithText(str(Res.string.password_set)).performClick()
        val fields = rule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("a long enough one")
        fields[1].performTextInput("a long enough one")

        rule.onNodeWithText(str(Res.string.password_save)).performClick()

        waitForText(str(Res.string.password_too_short))
    }

    @Test
    fun aFailedSaveIsReported() {
        `when`(app.security.changePassword(anyCharArray())).thenReturn(PasswordChange.FAILED)
        showPassword(hasPassword = false)
        enterPin("123456")
        rule.onNodeWithText(str(Res.string.password_set)).performClick()
        val fields = rule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("a long enough one")
        fields[1].performTextInput("a long enough one")

        rule.onNodeWithText(str(Res.string.password_save)).performClick()

        waitForText(str(Res.string.password_failed))
    }

    @Test
    fun thePasswordCanBeRemovedWhileThePinStands() {
        `when`(app.security.removePassword()).thenReturn(true)
        showPassword(hasPassword = true)
        enterPin("123456")

        rule.onNodeWithText(str(Res.string.password_remove)).performClick()
        rule.onNodeWithText(str(Res.string.password_remove_confirm)).performClick()

        waitForText(str(Res.string.password_removed))
        verify(app.security, times(1)).removePassword()
    }

    @Test
    fun thePinCanOnlyBeRemovedAfterTypingThePasswordAgain() {
        `when`(app.security.removePin()).thenReturn(true)
        showPassword(hasPassword = true)
        enterPin("123456")

        rule.onNodeWithText(str(Res.string.pin_remove)).performClick()
        rule.onNodeWithText(str(Res.string.pin_remove_confirm)).assertIsNotEnabled()
        verify(app.security, never()).removePin()
        typePassword("correct horse")
        rule.onNodeWithText(str(Res.string.pin_remove_confirm)).performClick()

        waitForText(str(Res.string.pin_removed))
        verify(app.security).verifyPassword(anyCharArray())
        verify(app.security).removePin()
    }

    @Test
    fun aWrongPasswordKeepsThePin() {
        showPassword(hasPassword = true)
        enterPin("123456")
        rule.onNodeWithText(str(Res.string.pin_remove)).performClick()
        `when`(app.security.verifyPassword(anyCharArray())).thenReturn(false)
        typePassword("not the password")

        rule.onNodeWithText(str(Res.string.pin_remove_confirm)).performClick()

        waitForText(str(Res.string.password_current_wrong))
        verify(app.security, never()).removePin()
    }

    @Test
    fun noRemovalIsOfferedWithoutThePinToFallBackOn() {
        showPassword(hasPassword = true, hasPin = false)
        typePassword("correct horse")
        rule.onNodeWithText(str(Res.string.action_continue)).performClick()
        waitForText(str(Res.string.password_change))

        assertEquals(0, countText(str(Res.string.password_remove)))
        assertEquals(0, countText(str(Res.string.pin_remove)))
    }
}
