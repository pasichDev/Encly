package com.pasich.encly.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.action_continue
import com.pasich.encly.core.resources.backup_reauth_title
import com.pasich.encly.core.resources.cancel
import com.pasich.encly.core.resources.security_encryption_active
import com.pasich.encly.core.resources.security_screenshots_title
import com.pasich.encly.core.resources.security_screenshots_warning_title
import com.pasich.encly.core.security.BiometricStatus
import com.pasich.encly.core.security.Mnemonic
import com.pasich.encly.platform.LocalScreenshotAllowance
import com.pasich.encly.presentation.designsystem.ProtectFromCapture
import com.pasich.encly.presentation.designsystem.RecoveryPhraseInput
import com.pasich.encly.presentation.designsystem.RecoveryPhraseState
import com.pasich.encly.presentation.designsystem.WordGrid
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.LockScreen
import com.pasich.encly.presentation.screen.PinCodeConfigScreen
import com.pasich.encly.presentation.screen.pincode.PinEntryScaffold
import com.pasich.encly.presentation.screen.settings.SecuritySettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Allow screenshots until the app locks": the switch on the Security page (warning, then the PIN, then on),
 * where it is not offered, and the screens that stay protected while it is on.
 */
class ScreenshotAllowanceScreensTest : ComposeScreenTest() {
    private val words = String(Mnemonic.generate())
    private val app by lazy { TestApp().withWorkingVault(words) }
    private val switchTitle by lazy { str(Res.string.security_screenshots_title) }

    private fun showSecurity(offered: Boolean = true) {
        app.withAuth(recoveryPhrase = true, biometric = BiometricStatus.UNAVAILABLE)
        setNavScreen(
            viewModels(app.securitySettings(), app.notePreviewsVm(), app.toolbarVm(), app.backup()),
            route = NavRoutes.SecuritySettingsRoute.name,
        ) { nav ->
            CompositionLocalProvider(LocalScreenshotAllowance provides app.screenshots.takeIf { offered }) {
                SecuritySettingsScreen(nav)
            }
        }
        waitForText(str(Res.string.security_encryption_active))
    }

    private fun switch() = rule.onNode(isToggleable() and hasText(switchTitle))

    @Test
    fun screenshotsAreOffByDefaultAndTheSwitchSaysSo() {
        showSecurity()

        assertFalse(app.screenshots.allowed.value)
        switch().assertExists()
    }

    @Test
    fun aHostThatDoesNotOfferItShowsNoSwitch() {
        showSecurity(offered = false)

        assertEquals(0, countText(switchTitle))
    }

    @Test
    fun turningItOnWarnsThenAsksForThePinAndOnlyThenAllows() {
        showSecurity()

        switch().performScrollTo().performClick()
        waitForText(str(Res.string.security_screenshots_warning_title))
        assertFalse(app.screenshots.allowed.value)

        rule.onNodeWithText(str(Res.string.action_continue)).performClick()
        waitForText(str(Res.string.backup_reauth_title))
        assertFalse(app.screenshots.allowed.value)

        enterPin("123456")
        waitFor(describe = { "allowed=${app.screenshots.allowed.value}" }) { app.screenshots.allowed.value }
    }

    @Test
    fun cancellingTheWarningOrThePinAllowsNothing() {
        showSecurity()

        switch().performScrollTo().performClick()
        waitForText(str(Res.string.security_screenshots_warning_title))
        rule.onNodeWithText(str(Res.string.cancel)).performClick()
        rule.waitForIdle()
        assertFalse(app.screenshots.allowed.value)

        switch().performScrollTo().performClick()
        waitForText(str(Res.string.security_screenshots_warning_title))
        rule.onNodeWithText(str(Res.string.action_continue)).performClick()
        waitForText(str(Res.string.backup_reauth_title))
        rule.onNodeWithText(str(Res.string.cancel)).performClick()
        rule.waitForIdle()
        assertFalse(app.screenshots.allowed.value)
    }

    @Test
    fun turningItOffNeedsNoPinAndEndsTheAllowance() {
        app.screenshots.allow()
        showSecurity()

        switch().performScrollTo().performClick()

        waitFor(describe = { "allowed=${app.screenshots.allowed.value}" }) { !app.screenshots.allowed.value }
        assertEquals(0, countText(str(Res.string.backup_reauth_title)))
    }

    // --- screens with a secret stay protected while screenshots are allowed -------------------

    @Test
    fun theLockScreenStaysProtectedWhileAllowed() {
        app.screenshots.allow()
        assertTrue(app.screenshots.capturable)

        setNavScreen(viewModels(app.lock()), route = NavRoutes.LockRoute.name) { nav ->
            CompositionLocalProvider(LocalScreenshotAllowance provides app.screenshots) { LockScreen(nav) }
        }

        assertTrue(app.screenshots.allowed.value)
        assertFalse(app.screenshots.capturable)
    }

    @Test
    fun pinEntryStaysProtectedWhileAllowedAndTheWindowOpensAgainWhenItIsGone() {
        app.screenshots.allow()
        var shown by mutableStateOf(true)
        setScreen {
            CompositionLocalProvider(LocalScreenshotAllowance provides app.screenshots) {
                if (shown) PinEntryScaffold(title = "t", subtitle = "s") {}
            }
        }
        assertFalse(app.screenshots.capturable)

        rule.runOnIdle { shown = false }
        rule.waitForIdle()

        assertTrue(app.screenshots.capturable)
    }

    @Test
    fun thePinChangeScreenStaysProtectedWhileAllowed() {
        app.screenshots.allow()
        setNavScreen(
            viewModels(app.securitySettings()),
            route = NavRoutes.PinCodeConfig.name,
        ) { nav ->
            CompositionLocalProvider(LocalScreenshotAllowance provides app.screenshots) { PinCodeConfigScreen(nav) }
        }

        assertFalse(app.screenshots.capturable)
    }

    @Test
    fun recoveryWordsStayProtectedWhileAllowedWhetherShownOrTyped() {
        app.screenshots.allow()
        var mode by mutableStateOf(Words.NONE)
        setScreen {
            CompositionLocalProvider(LocalScreenshotAllowance provides app.screenshots) {
                when (mode) {
                    Words.SHOWN -> WordGrid(words = words.split(' '), hidden = false)
                    Words.TYPED -> RecoveryPhraseInput(RecoveryPhraseState())
                    Words.NONE -> Unit
                }
            }
        }
        assertTrue(app.screenshots.capturable)

        rule.runOnIdle { mode = Words.SHOWN }
        rule.waitForIdle()
        assertFalse(app.screenshots.capturable)

        rule.runOnIdle { mode = Words.NONE }
        rule.waitForIdle()
        assertTrue(app.screenshots.capturable)

        rule.runOnIdle { mode = Words.TYPED }
        rule.waitForIdle()
        assertFalse(app.screenshots.capturable)
    }

    @Test
    fun theHoldIsReleasedWhenTheScreenLeavesEvenIfAskedTwice() {
        app.screenshots.allow()
        var count by mutableIntStateOf(2)
        setScreen {
            CompositionLocalProvider(LocalScreenshotAllowance provides app.screenshots) {
                repeat(count) { key(it) { ProtectFromCapture() } }
            }
        }
        assertFalse(app.screenshots.capturable)

        rule.runOnIdle { count = 1 }
        rule.waitForIdle()
        assertFalse(app.screenshots.capturable)

        rule.runOnIdle { count = 0 }
        rule.waitForIdle()
        assertTrue(app.screenshots.capturable)
    }

    private enum class Words { NONE, SHOWN, TYPED }
}
