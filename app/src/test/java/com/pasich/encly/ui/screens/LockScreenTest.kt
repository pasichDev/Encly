package com.pasich.encly.ui.screens

import android.app.Activity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import com.pasich.encly.R
import com.pasich.encly.core.security.VaultUnlockResult
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.LockScreen
import com.pasich.encly.testutil.anyCharArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalTestApi::class)
class LockScreenTest : ComposeScreenTest() {
    private val app by lazy { TestApp(context) }

    private fun show(recovery: Boolean = true) {
        `when`(app.security.hasRecoverySeed()).thenReturn(recovery)
        setNavScreen(viewModels(app.lock()), route = NavRoutes.LockRoute.name) { nav ->
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            activity = LocalActivity.current
            LockScreen(nav)
        }
    }

    private lateinit var back: OnBackPressedDispatcher
    private var activity: Activity? = null

    /**
     * The lock screen over a vault screen, as after a re-lock whose navigation left Home below it:
     * the worst case, where a Back that got through would reveal the notes.
     */
    private fun showOverTheVault() {
        `when`(app.security.hasRecoverySeed()).thenReturn(true)
        setNavScreen(
            viewModels(app.lock()),
            route = NavRoutes.LockRoute.name,
            start = NavRoutes.HomeRoute.name,
        ) { nav ->
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            activity = LocalActivity.current
            LockScreen(nav)
        }
        navigateTo(NavRoutes.LockRoute.name)
        assertStillLocked()
    }

    private fun pressBack() {
        rule.runOnIdle { back.onBackPressed() }
        rule.waitForIdle()
    }

    /** Still on the lock screen; the vault screen below was never drawn and the app did not close. */
    private fun assertStillLocked() {
        assertEquals(NavRoutes.LockRoute.name, currentRoute())
        assertEquals(0, countText("stub:${NavRoutes.HomeRoute.name}"))
        assertFalse(activity?.isFinishing == true)
    }

    private fun anyChars(): CharArray = ArgumentMatchers.any(CharArray::class.java) ?: CharArray(0)

    @Test
    fun showsThePinPad() {
        show()

        rule.onNodeWithText(str(R.string.lock_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.lock_subtitle)).assertIsDisplayed()
        (0..9).forEach { rule.onNodeWithText(it.toString()).assertIsDisplayed() }
        rule.onNodeWithContentDescription(str(R.string.pin_delete_digit)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.lock_use_recovery_phrase)).assertIsDisplayed()
        assertBodyNotEmpty(minTexts = 10)
    }

    @Test
    fun withoutARecoveryPhraseThereIsNoRecoveryLink() {
        show(recovery = false)

        assertEquals(0, countText(str(R.string.lock_use_recovery_phrase)))
    }

    @Test
    fun theDotsFollowTheDigitsAndBackspace() {
        show()

        typePin("12")
        rule.onNodeWithContentDescription(str(R.string.pin_digits_entered, 2, 6)).assertExists()
        rule.onNodeWithContentDescription(str(R.string.pin_delete_digit)).performClick()
        rule.onNodeWithContentDescription(str(R.string.pin_digits_entered, 1, 6)).assertExists()
    }

    @Test
    fun aWrongPinSaysSoAndStays() {
        `when`(app.security.unlockWithPin(anyCharArray())).thenReturn(VaultUnlockResult.INVALID_CREDENTIAL)
        show()

        typePin("000000")

        waitForText(str(R.string.lock_wrong_pin))
        rule.onNodeWithContentDescription(str(R.string.pin_digits_entered, 0, 6)).assertExists()
        assertEquals(NavRoutes.LockRoute.name, currentRoute())
    }

    @Test
    fun theRightPinOpensTheNotes() {
        `when`(app.security.unlockWithPin(anyCharArray())).thenReturn(VaultUnlockResult.SUCCESS)
        show()

        typePin("123456")

        waitFor { currentRoute() == NavRoutes.HomeRoute.name }
    }

    @Test
    fun theRecoveryFormHasTwelveCellsAndWaitsForAValidPhrase() {
        show()
        rule.onNodeWithText(str(R.string.lock_use_recovery_phrase)).performClick()

        rule.onNodeWithText(str(R.string.lock_recovery_title)).assertIsDisplayed()
        (1..12).forEach { phraseCell(it).assertExists() }
        rule.onNodeWithText(str(R.string.lock_recover_access)).assertIsNotEnabled()

        phraseCell(1).performTextReplacement(PHRASE)

        assertEquals(PHRASE.split(' '), phraseCells())
        rule.onNodeWithText(str(R.string.lock_recover_access)).assertIsEnabled()
    }

    @Test
    fun spaceMovesOnAndBackspaceInAnEmptyCellGoesBack() {
        show()
        rule.onNodeWithText(str(R.string.lock_use_recovery_phrase)).performClick()
        phraseCell(1).performClick()

        phraseCell(1).performTextInput("abandon ")
        phraseCell(2).assertIsFocused()
        assertEquals("abandon", phraseCells()[0])

        phraseCell(2).performKeyInput { pressKey(Key.Backspace) }
        phraseCell(1).assertIsFocused()
    }

    @Test
    fun nextOnTheKeyboardMovesToTheNextCell() {
        show()
        rule.onNodeWithText(str(R.string.lock_use_recovery_phrase)).performClick()
        phraseCell(4).performTextInput("zoo")

        phraseCell(4).performImeAction()

        phraseCell(5).assertIsFocused()
    }

    @Test
    fun capitalsAndDigitsAreCleanedFromAWord() {
        show()
        rule.onNodeWithText(str(R.string.lock_use_recovery_phrase)).performClick()

        phraseCell(1).performTextInput("Zoo1")

        assertEquals("zoo", phraseCells()[0])
    }

    @Test
    fun aWordOutsideTheWordlistIsFlagged() {
        show()
        rule.onNodeWithText(str(R.string.lock_use_recovery_phrase)).performClick()

        phraseCell(3).performTextInput("qqq")

        rule.onNodeWithText(str(R.string.recovery_word_unknown, 3)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.lock_recover_access)).assertIsNotEnabled()
    }

    @Test
    fun aWrongPhraseSaysSoAndKeepsTheWords() {
        `when`(app.security.unlockWithSeed(anyChars())).thenReturn(VaultUnlockResult.INVALID_CREDENTIAL)
        show()
        rule.onNodeWithText(str(R.string.lock_use_recovery_phrase)).performClick()
        phraseCell(1).performTextReplacement(PHRASE)

        rule.onNodeWithText(str(R.string.lock_recover_access)).performClick()

        waitForText(str(R.string.lock_wrong_recovery_phrase))
        assertEquals(PHRASE.split(' '), phraseCells())
    }

    @Test
    fun theRightPhraseAsksForANewPin() {
        `when`(app.security.unlockWithSeed(anyChars())).thenReturn(VaultUnlockResult.SUCCESS)
        show()
        rule.onNodeWithText(str(R.string.lock_use_recovery_phrase)).performClick()
        phraseCell(1).performTextReplacement(PHRASE)

        rule.onNodeWithText(str(R.string.lock_recover_access)).performClick()

        waitFor { currentRoute() == NavRoutes.PinCodeConfig.name }
    }

    @Test
    fun backOnThePinPadNeverLeavesTheLockScreen() {
        showOverTheVault()
        typePin("12")

        repeat(3) {
            pressBack()
            assertStillLocked()
        }
        rule.onNodeWithText(str(R.string.lock_title)).assertIsDisplayed()
    }

    @Test
    fun backWhenTheLockScreenIsAllThereIsDoesNotCloseTheApp() {
        show()

        pressBack()

        assertStillLocked()
        rule.onNodeWithText(str(R.string.lock_title)).assertIsDisplayed()
    }

    @Test
    fun backFromTheRecoveryFormReturnsToThePinPadNotTheVault() {
        showOverTheVault()
        rule.onNodeWithText(str(R.string.lock_use_recovery_phrase)).performClick()
        rule.onNodeWithText(str(R.string.lock_recovery_title)).assertIsDisplayed()

        pressBack()

        assertStillLocked()
        rule.onNodeWithText(str(R.string.lock_title)).assertIsDisplayed()
        assertEquals(0, countText(str(R.string.lock_recovery_title)))

        pressBack()
        assertStillLocked()
    }

    @Test
    fun backWhileThePinIsCheckedStaysOnTheLockScreen() {
        val checking = CountDownLatch(1)
        val release = CountDownLatch(1)
        `when`(app.security.unlockWithPin(anyCharArray())).thenAnswer {
            checking.countDown()
            release.await(WAIT_MS, TimeUnit.MILLISECONDS)
            VaultUnlockResult.INVALID_CREDENTIAL
        }
        showOverTheVault()
        typePin("000000")
        assertTrue(checking.await(WAIT_MS, TimeUnit.MILLISECONDS))

        pressBack()
        assertStillLocked()

        release.countDown()
        waitForText(str(R.string.lock_wrong_pin))
        assertStillLocked()
    }

    @Test
    fun backToPinDropsTheWords() {
        show()
        rule.onNodeWithText(str(R.string.lock_use_recovery_phrase)).performClick()
        phraseCell(1).performTextReplacement(PHRASE)

        rule.onNodeWithText(str(R.string.lock_back_to_pin)).performClick()
        rule.onNodeWithText(str(R.string.lock_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.lock_use_recovery_phrase)).performClick()

        assertTrue(phraseCells().all(String::isEmpty))
    }
}
