package com.pasich.encly.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.backup_reauth_title
import com.pasich.encly.core.resources.lock_wrong_pin
import com.pasich.encly.core.resources.pin_create_title
import com.pasich.encly.core.resources.pin_digits_count
import com.pasich.encly.core.resources.wipe_pin_confirm_title
import com.pasich.encly.core.resources.wipe_pin_intro
import com.pasich.encly.core.resources.wipe_pin_new_title
import com.pasich.encly.core.resources.wipe_pin_set
import com.pasich.encly.core.security.PinCheck
import com.pasich.encly.presentation.navigation.LocalVaultLocked
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.PinCodeConfigScreen
import com.pasich.encly.presentation.screen.backup.BackupScreen
import com.pasich.encly.presentation.screen.settings.WipePinScreen
import com.pasich.encly.presentation.viewmodel.BackupAction
import com.pasich.encly.testutil.anyCharArray
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The PIN path of what the passphrase screens do too: one check at a time with the keypad waiting, the error of a
 * repeated wrong PIN announced again, and nothing typed or kept once the vault locks.
 */
@OptIn(ExperimentalTestApi::class)
class PinPathBusyAndLockTest : ComposeScreenTest() {
    private val app by lazy { TestApp() }
    private var vaultLocked by mutableStateOf(false)

    private fun blockedVerify(answer: Boolean): CountDownLatch {
        val release = CountDownLatch(1)
        `when`(app.security.verifyPinResult(anyCharArray())).thenAnswer {
            release.await(10, TimeUnit.SECONDS)
            if (answer) PinCheck.OK else PinCheck.WRONG
        }
        return release
    }

    private fun assertKeypadWaits() = rule.onNode(hasText("5") and hasClickAction()).assertIsNotEnabled()

    private fun showPinChange(requiresCurrent: Boolean) {
        `when`(app.security.canResetPinWithoutCurrent()).thenReturn(!requiresCurrent)
        setNavScreen(
            viewModels(app.securitySettings()),
            route = NavRoutes.PinCodeConfig.name,
            start = NavRoutes.SecuritySettingsRoute.name,
        ) { nav -> CompositionLocalProvider(LocalVaultLocked provides vaultLocked) { PinCodeConfigScreen(nav) } }
        navigateTo(NavRoutes.PinCodeConfig.name)
    }

    @Test
    fun theReauthenticationKeypadWaitsWhileACheckRunsAndTheErrorIsAnnouncedAgainAfterADigit() {
        val release = blockedVerify(answer = false)
        val backup = app.backup()
        setNavScreen(viewModels(backup, app.markdownExport()), route = NavRoutes.BackupRoute.name) { nav ->
            BackupScreen(nav)
        }
        rule.runOnIdle { backup.start(BackupAction.IMPORT) }
        waitForText(str(Res.string.backup_reauth_title))

        enterPin("123456")

        waitFor { backup.uiState.value.busy }
        assertKeypadWaits()
        release.countDown()
        waitForText(str(Res.string.lock_wrong_pin))
        verify(app.security, times(1)).verifyPinResult(anyCharArray())

        typePin("1")

        assertEquals(0, countText(str(Res.string.lock_wrong_pin)))
    }

    @Test
    fun theChangeKeypadWaitsWhileTheCurrentPinIsCheckedAndItIsCheckedOnce() {
        val release = blockedVerify(answer = true)
        showPinChange(requiresCurrent = true)

        enterPin("123456")

        waitFor { rule.onAllNodes(hasText("5") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        assertKeypadWaits()
        release.countDown()
        waitForText(str(Res.string.pin_create_title))
        verify(app.security, times(1)).verifyPinResult(anyCharArray())
    }

    @Test
    fun aLockInTheMiddleOfAPinChangeDropsTheDigitsAndTheKeypadTakesNoMore() {
        showPinChange(requiresCurrent = false)
        typePin("123")
        rule.onNodeWithContentDescription(str(Res.string.pin_digits_count, 3)).assertExists()

        rule.runOnIdle { vaultLocked = true }
        rule.waitForIdle()

        rule.onNodeWithContentDescription(str(Res.string.pin_digits_count, 0)).assertExists()
        assertKeypadWaits()
    }

    @Test
    fun aLockInTheMiddleOfAWipePinDropsTheKeptFirstEntryAndGoesBackToTheOptions() {
        `when`(app.security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.OK)
        setNavScreen(
            viewModels(app.securitySettings()),
            route = NavRoutes.WipePinRoute.name,
            start = NavRoutes.SecuritySettingsRoute.name,
        ) { nav -> CompositionLocalProvider(LocalVaultLocked provides vaultLocked) { WipePinScreen(nav) } }
        navigateTo(NavRoutes.WipePinRoute.name)
        enterPin("123456")
        waitForText(str(Res.string.wipe_pin_intro))
        scrollToText(str(Res.string.wipe_pin_set))
        rule.onNodeWithText(str(Res.string.wipe_pin_set)).also { it.assertExists() }
        rule.onNode(hasText(str(Res.string.wipe_pin_set)) and hasClickAction()).performClickAndIdle()
        waitForText(str(Res.string.wipe_pin_new_title))
        enterPin("864200")
        waitForText(str(Res.string.wipe_pin_confirm_title))

        rule.runOnIdle { vaultLocked = true }
        rule.waitForIdle()

        waitForText(str(Res.string.wipe_pin_intro))
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performClickAndIdle() {
        performClick()
        rule.waitForIdle()
    }
}
