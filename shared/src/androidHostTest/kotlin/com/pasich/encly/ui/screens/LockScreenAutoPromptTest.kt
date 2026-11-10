package com.pasich.encly.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.pin_unlock_with_fingerprint
import com.pasich.encly.platform.LocalBiometricHost
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.LockScreen
import com.pasich.encly.testutil.TestScreen
import com.pasich.encly.testutil.anyCallback
import com.pasich.encly.testutil.eqValue
import org.junit.Test
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * The lock screen's automatic fingerprint prompt: the auto-lock puts the lock screen up while the
 * app is in the background, where the system would cancel a prompt unseen and leave the ViewModel
 * believing one is open. The prompt waits for the window to be in front, and the button works
 * whenever no prompt is really showing.
 */
@OptIn(ExperimentalTestApi::class)
class LockScreenAutoPromptTest : ComposeScreenTest() {
    private val app by lazy { TestApp() }

    private fun show(screen: TestScreen) {
        `when`(app.security.isBiometricEnabled()).thenReturn(true)
        `when`(app.security.biometricAvailable()).thenReturn(true)
        setNavScreen(viewModels(app.lock()), route = NavRoutes.LockRoute.name) { nav ->
            CompositionLocalProvider(LocalBiometricHost provides screen.host) { LockScreen(nav) }
        }
    }

    private fun promptsShown(): Int = mockingDetails(app.security).invocations
        .count { it.method.name == "requestBiometricKey" }

    private fun onUi(block: () -> Unit) = rule.runOnIdle(block).also { rule.waitForIdle() }

    @Test
    fun aLockScreenShownInTheBackgroundPromptsOnceTheAppIsResumed() {
        val screen = TestScreen(Lifecycle.State.CREATED)
        show(screen)
        rule.waitForIdle()
        verify(app.security, never()).requestBiometricKey(eqValue(screen.biometric), anyCallback())

        onUi { screen.resume() }

        rule.waitUntil(WAIT_MS) { promptsShown() == 1 }
    }

    @Test
    fun aStartedButNotResumedActivityGetsNoAutomaticPrompt() {
        // The lock screen composes at ON_START after a background auto-lock; the reporter's fix was to wait
        // for RESUMED.
        val screen = TestScreen(Lifecycle.State.STARTED)
        show(screen)
        rule.waitForIdle()
        verify(app.security, never()).requestBiometricKey(eqValue(screen.biometric), anyCallback())

        onUi { screen.resume() }

        rule.waitUntil(WAIT_MS) { promptsShown() == 1 }
    }

    @Test
    fun aPromptIsNotRepeatedWhenTheAppComesBackAgain() {
        val screen = TestScreen()
        show(screen)
        rule.waitUntil(WAIT_MS) { promptsShown() == 1 }

        onUi { screen.stop() }
        onUi { screen.resume() }

        verify(app.security, times(1)).requestBiometricKey(eqValue(screen.biometric), anyCallback())
    }

    @Test
    fun theFingerprintButtonPromptsAgainAfterAPromptWasCutOff() {
        val screen = TestScreen()
        show(screen)
        rule.waitUntil(WAIT_MS) { promptsShown() == 1 }
        onUi { screen.stop() }
        onUi { screen.resume() }

        rule.onNodeWithContentDescription(str(Res.string.pin_unlock_with_fingerprint)).performClick()

        rule.waitUntil(WAIT_MS) { promptsShown() == 2 }
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
