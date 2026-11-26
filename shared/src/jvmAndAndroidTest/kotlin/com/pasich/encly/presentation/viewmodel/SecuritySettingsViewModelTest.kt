package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.core.common.UiText
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.biometric_change_not_confirmed
import com.pasich.encly.core.resources.biometric_enroll_failed
import com.pasich.encly.core.resources.error_keystore_unavailable
import com.pasich.encly.core.resources.key_not_found_title
import com.pasich.encly.core.resources.key_store_unavailable
import com.pasich.encly.core.resources.keyring_no_default
import com.pasich.encly.core.resources.keyring_not_running
import com.pasich.encly.core.resources.keyring_prompt_dismissed
import com.pasich.encly.core.resources.lock_pin_key_lost
import com.pasich.encly.core.resources.pin_current_wrong
import com.pasich.encly.core.resources.pin_update_failed
import com.pasich.encly.core.security.AuthSettings
import com.pasich.encly.core.security.AuthType
import com.pasich.encly.core.security.AutoLock
import com.pasich.encly.core.security.BiometricStatus
import com.pasich.encly.core.security.DuressPinChange
import com.pasich.encly.core.security.KeyStoreProblem
import com.pasich.encly.core.security.KeyboardPrivacy
import com.pasich.encly.core.security.PinChange
import com.pasich.encly.core.security.PinCheck
import com.pasich.encly.core.security.ScreenshotAllowance
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.WipePinChange
import com.pasich.encly.testutil.InMemoryKeyValueStore
import com.pasich.encly.testutil.TestScreen
import com.pasich.encly.testutil.answerCallback
import com.pasich.encly.testutil.anyCallback
import com.pasich.encly.testutil.anyCharArray
import com.pasich.encly.testutil.eqValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/** Settings → Security: the loaded state, changing the PIN and switching biometrics. */
@OptIn(ExperimentalCoroutinesApi::class)
class SecuritySettingsViewModelTest {
    private lateinit var security: SecurityManager
    private lateinit var viewModel: SecuritySettingsViewModel
    private val screen = TestScreen()
    private val screenshots = ScreenshotAllowance()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        security = mock(SecurityManager::class.java)
        `when`(security.getSettingsAuth()).thenReturn(
            AuthSettings(authType = AuthType.PIN, isBiometricEnabled = true, isUserCreatedSeedKey = false),
        )
        `when`(security.biometricStatus()).thenReturn(BiometricStatus.AVAILABLE)
        viewModel =
            SecuritySettingsViewModel(
                security,
                KeyboardPrivacy(InMemoryKeyValueStore()),
                AutoLock(InMemoryKeyValueStore()),
                screenshots,
            )
        // The first load reads the mock on an IO thread; stubbing it meanwhile would race.
        runBlocking { viewModel.uiState.first { it.loaded } }
    }

    @Test
    fun screenshotsAreBlockedUntilAllowedAndTurningThemOffEndsTheAllowance() {
        assertFalse(viewModel.screenshotsAllowed.value)

        screenshots.allow()
        assertTrue(viewModel.screenshotsAllowed.value)

        viewModel.stopAllowingScreenshots()
        assertFalse(viewModel.screenshotsAllowed.value)
        assertFalse(screenshots.capturable)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun nothingIsShownUntilTheFirstLoadAndThenTheVaultsState() = runTest {
        val state = viewModel.uiState.first { it.loaded }

        assertEquals(AuthType.PIN, state.authType)
        assertTrue(state.biometricEnable)
        assertTrue(state.isBiometricAvailable)
        assertFalse(state.isUserCreatedSeedKey)
        assertNull(state.error)
    }

    @Test
    fun refreshPicksUpARecoveryPhraseAddedMeanwhile() = runTest {
        viewModel.uiState.first { it.loaded }
        `when`(security.getSettingsAuth()).thenReturn(
            AuthSettings(authType = AuthType.PIN, isBiometricEnabled = false, isUserCreatedSeedKey = true),
        )
        `when`(security.biometricStatus()).thenReturn(BiometricStatus.NOT_ENROLLED)

        viewModel.refresh()

        val state = viewModel.uiState.first { it.isUserCreatedSeedKey }
        assertFalse(state.biometricEnable)
        assertFalse(state.isBiometricAvailable)
    }

    @Test
    fun aWrongCurrentPinIsReportedAndARightOneIsNot() = runTest {
        viewModel.uiState.first { it.loaded }
        `when`(security.verifyPinResult("111111".toCharArray())).thenReturn(PinCheck.WRONG)
        `when`(security.verifyPinResult("222222".toCharArray())).thenReturn(PinCheck.OK)

        assertFalse(checkCurrent("111111"))
        assertEquals(UiText.of(Res.string.pin_current_wrong), viewModel.uiState.value.error)

        viewModel.clearError()
        assertTrue(checkCurrent("222222"))
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun aKeyStoreThatDidNotAnswerIsNeverReportedAsAWrongSecret() = runTest {
        viewModel.uiState.first { it.loaded }
        val cases = listOf(
            Triple(PinCheck.KEY_STORE_UNAVAILABLE, KeyStoreProblem.NOT_RUNNING, Res.string.keyring_not_running),
            Triple(PinCheck.KEY_STORE_UNAVAILABLE, KeyStoreProblem.LOCKED, Res.string.key_store_unavailable),
            Triple(PinCheck.KEY_STORE_UNAVAILABLE, KeyStoreProblem.NO_DEFAULT, Res.string.keyring_no_default),
            Triple(PinCheck.KEY_STORE_UNAVAILABLE, KeyStoreProblem.DISMISSED, Res.string.keyring_prompt_dismissed),
            Triple(PinCheck.KEY_STORE_UNAVAILABLE, null, Res.string.key_store_unavailable),
            Triple(PinCheck.KEY_NOT_FOUND, null, Res.string.key_not_found_title),
            Triple(PinCheck.KEY_STORE_FAILED, null, Res.string.error_keystore_unavailable),
            Triple(PinCheck.KEY_LOST, null, Res.string.lock_pin_key_lost),
        )
        for ((check, problem, message) in cases) {
            viewModel.clearError()
            `when`(security.verifyPinResult("555555".toCharArray())).thenReturn(check)
            `when`(security.keyStoreProblem()).thenReturn(problem)

            val result = CompletableDeferred<PinCheck>()
            viewModel.verifyCurrentPin("555555".toCharArray()) { result.complete(it) }

            assertEquals(check, result.await())
            assertEquals(UiText.of(message), viewModel.uiState.value.error)
            assertEquals(problem, viewModel.keyStoreProblem())
        }
    }

    @Test
    fun aKeyStoreThatIsNotUsableSaysWhatIsMissingWhenThePinIsChanged() = runTest {
        viewModel.uiState.first { it.loaded }
        `when`(security.changePin("333333".toCharArray())).thenReturn(PinChange.KEY_STORE_UNAVAILABLE)
        `when`(security.keyStoreProblem()).thenReturn(KeyStoreProblem.NOT_RUNNING)

        assertFalse(activate("333333"))

        assertEquals(UiText.of(Res.string.keyring_not_running), viewModel.uiState.value.error)
    }

    @Test
    fun aNewPinIsStoredOrItsFailureShown() = runTest {
        viewModel.uiState.first { it.loaded }
        `when`(security.changePin("333333".toCharArray())).thenReturn(PinChange.SET)
        `when`(security.changePin("444444".toCharArray())).thenReturn(PinChange.FAILED)

        assertTrue(activate("333333"))
        assertEquals(AuthType.PIN, viewModel.uiState.value.authType)
        assertNull(viewModel.uiState.value.error)

        assertFalse(activate("444444"))
        assertEquals(UiText.of(Res.string.pin_update_failed), viewModel.uiState.value.error)
    }

    @Test
    fun aLockedKeyringIsNamedWhenANewPinIsStored() = runTest {
        viewModel.uiState.first { it.loaded }
        `when`(security.changePin("666666".toCharArray())).thenReturn(PinChange.KEY_STORE_UNAVAILABLE)

        assertFalse(activate("666666"))
        assertEquals(UiText.of(Res.string.key_store_unavailable), viewModel.uiState.value.error)
    }

    @Test
    fun aPinEqualToTheDuressPinIsRefusedWithoutAnError() = runTest {
        viewModel.uiState.first { it.loaded }
        `when`(security.changePin("555555".toCharArray())).thenReturn(PinChange.IN_USE)

        assertFalse(activate("555555"))
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun aDuressPinIsHandedOnAndRemembered() = runTest {
        viewModel.uiState.first { it.loaded }
        `when`(security.configureDuressPin(anyCharArray())).thenReturn(DuressPinChange.SET_NEW_VAULT)
        val result = CompletableDeferred<DuressPinChange>()

        viewModel.setDuressPin("864200".toCharArray()) { result.complete(it) }

        assertEquals(DuressPinChange.SET_NEW_VAULT, result.await())
        assertTrue(viewModel.uiState.value.hasDuressPin)
    }

    @Test
    fun turningTheDuressPinOffForgetsIt() = runTest {
        `when`(security.getSettingsAuth()).thenReturn(
            AuthSettings(AuthType.PIN, isBiometricEnabled = false, isUserCreatedSeedKey = false, hasDuressPin = true),
        )
        viewModel.refresh()
        viewModel.uiState.first { it.hasDuressPin }
        `when`(security.removeDuressPin()).thenReturn(true)
        val result = CompletableDeferred<Boolean>()

        viewModel.removeDuressPin { result.complete(it) }

        assertTrue(result.await())
        assertFalse(viewModel.uiState.value.hasDuressPin)
    }

    @Test
    fun theCheckedAndTheNewPinAreWipedAfterUse() = runTest {
        viewModel.uiState.first { it.loaded }
        `when`(security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.OK)
        `when`(security.changePin(anyCharArray())).thenReturn(PinChange.FAILED)

        val current = "111111".toCharArray()
        assertTrue(checkCurrent(current))
        assertArrayEquals(CharArray(current.size), current)

        val new = "222222".toCharArray()
        assertFalse(activate(new))
        assertArrayEquals(CharArray(new.size), new)
    }

    @Test
    fun afterARecoveryUnlockTheNewPinIsSetWithoutTheOldOne() {
        `when`(security.canResetPinWithoutCurrent()).thenReturn(true)
        assertFalse(viewModel.requiresCurrentPin())

        `when`(security.canResetPinWithoutCurrent()).thenReturn(false)
        assertTrue(viewModel.requiresCurrentPin())

        `when`(security.pinLockoutRemainingMillis()).thenReturn(LOCKOUT_MS)
        assertEquals(LOCKOUT_MS, viewModel.pinLockoutRemainingMillis())
    }

    @Test
    fun aWipePinIsHandedOnAndClearsTheTurnedOffNotice() = runTest {
        `when`(security.getSettingsAuth()).thenReturn(
            AuthSettings(
                AuthType.PIN,
                isBiometricEnabled = false,
                isUserCreatedSeedKey = false,
                wipePinTurnedOff = true,
            ),
        )
        viewModel.refresh()
        viewModel.uiState.first { it.wipePinTurnedOff }
        `when`(security.configureWipePin(anyCharArray())).thenReturn(WipePinChange.SET)
        val result = CompletableDeferred<WipePinChange>()

        viewModel.setWipePin("864200".toCharArray()) { result.complete(it) }

        assertEquals(WipePinChange.SET, result.await())
        assertFalse(viewModel.uiState.value.wipePinTurnedOff)
    }

    @Test
    fun aRefusedWipePinKeepsTheNotice() = runTest {
        `when`(security.getSettingsAuth()).thenReturn(
            AuthSettings(
                AuthType.PIN,
                isBiometricEnabled = false,
                isUserCreatedSeedKey = false,
                wipePinTurnedOff = true,
            ),
        )
        viewModel.refresh()
        viewModel.uiState.first { it.wipePinTurnedOff }
        `when`(security.configureWipePin(anyCharArray())).thenReturn(WipePinChange.SAME_AS_PIN)
        val result = CompletableDeferred<WipePinChange>()

        viewModel.setWipePin("135790".toCharArray()) { result.complete(it) }

        assertEquals(WipePinChange.SAME_AS_PIN, result.await())
        assertTrue(viewModel.uiState.value.wipePinTurnedOff)
    }

    @Test
    fun turningTheWipePinOffReportsTheResult() = runTest {
        `when`(security.removeWipePin()).thenReturn(true)
        val result = CompletableDeferred<Boolean>()

        viewModel.removeWipePin { result.complete(it) }

        assertTrue(result.await())
        verify(security).removeWipePin()
    }

    @Test
    fun enablingBiometricsEnrollsAndShowsTheResult() = runTest {
        viewModel.uiState.first { it.loaded }
        answerCallback(true).`when`(security).enrollBiometric(eqValue(screen.biometric), anyCallback())
        `when`(security.isBiometricEnabled()).thenReturn(true)

        viewModel.toggleBiometric(screen.host, enable = true)

        assertTrue(viewModel.uiState.value.biometricEnable)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun aFailedEnrollmentLeavesBiometricsOffWithAMessage() = runTest {
        viewModel.uiState.first { it.loaded }
        answerCallback(false).`when`(security).enrollBiometric(eqValue(screen.biometric), anyCallback())

        viewModel.toggleBiometric(screen.host, enable = true)

        assertFalse(viewModel.uiState.value.biometricEnable)
        assertEquals(UiText.of(Res.string.biometric_enroll_failed), viewModel.uiState.value.error)
    }

    @Test
    fun disablingBiometricsNeedsAConfirmedPrompt() = runTest {
        viewModel.uiState.first { it.loaded }
        answerCallback(true).`when`(security).confirmBiometric(eqValue(screen.biometric), anyCallback())

        viewModel.toggleBiometric(screen.host, enable = false)

        verify(security).disableBiometric()
        assertFalse(viewModel.uiState.value.biometricEnable)
    }

    @Test
    fun anUnconfirmedDisableKeepsBiometricsOn() = runTest {
        viewModel.uiState.first { it.loaded }
        answerCallback(false).`when`(security).confirmBiometric(eqValue(screen.biometric), anyCallback())
        `when`(security.isBiometricEnabled()).thenReturn(true)

        viewModel.toggleBiometric(screen.host, enable = false)

        verify(security, never()).disableBiometric()
        assertTrue(viewModel.uiState.value.biometricEnable)
        assertEquals(UiText.of(Res.string.biometric_change_not_confirmed), viewModel.uiState.value.error)
    }

    @Test
    fun tapsWhileTheEnrolPromptIsOpenAreIgnored() = runTest {
        viewModel.uiState.first { it.loaded }
        // The prompt never answers: it stays open.
        viewModel.toggleBiometric(screen.host, enable = true)
        viewModel.toggleBiometric(screen.host, enable = true)
        viewModel.toggleBiometric(screen.host, enable = false)

        assertTrue(viewModel.biometricInFlight)
        verify(security, times(1)).enrollBiometric(eqValue(screen.biometric), anyCallback())
        verify(security, never()).confirmBiometric(eqValue(screen.biometric), anyCallback())
    }

    @Test
    fun tapsWhileTheDisablePromptIsOpenAreIgnored() = runTest {
        viewModel.uiState.first { it.loaded }
        viewModel.toggleBiometric(screen.host, enable = false)
        viewModel.toggleBiometric(screen.host, enable = false)
        viewModel.toggleBiometric(screen.host, enable = true)

        verify(security, times(1)).confirmBiometric(eqValue(screen.biometric), anyCallback())
        verify(security, never()).enrollBiometric(eqValue(screen.biometric), anyCallback())
        verify(security, never()).disableBiometric()
    }

    @Test
    fun onceThePromptAnswersTheSwitchWorksAgain() = runTest {
        viewModel.uiState.first { it.loaded }
        val answer = arrayOfNulls<(Boolean) -> Unit>(1)
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            answer[0] = invocation.arguments.last() as (Boolean) -> Unit
            null
        }.`when`(security).enrollBiometric(eqValue(screen.biometric), anyCallback())

        viewModel.toggleBiometric(screen.host, enable = true)
        viewModel.toggleBiometric(screen.host, enable = true)
        answer[0]!!(false)

        assertFalse(viewModel.biometricInFlight)
        viewModel.toggleBiometric(screen.host, enable = true)
        verify(security, times(2)).enrollBiometric(eqValue(screen.biometric), anyCallback())
    }

    @Test
    fun aRotationDuringThePromptDoesNotLeaveTheSwitchStuck() = runTest {
        viewModel.uiState.first { it.loaded }
        viewModel.toggleBiometric(screen.host, enable = true)
        assertTrue(viewModel.biometricInFlight)

        // androidx.biometric drops the answer of a prompt whose activity is gone.
        screen.destroy()

        assertFalse(viewModel.biometricInFlight)
        val recreated = TestScreen()
        viewModel.toggleBiometric(recreated.host, enable = true)
        verify(security).enrollBiometric(eqValue(recreated.biometric), anyCallback())
    }

    private suspend fun checkCurrent(pin: String): Boolean = checkCurrent(pin.toCharArray())

    private suspend fun checkCurrent(pin: CharArray): Boolean {
        val result = CompletableDeferred<PinCheck>()
        viewModel.verifyCurrentPin(pin) { result.complete(it) }
        return result.await() == PinCheck.OK
    }

    private suspend fun activate(pin: String): Boolean = activate(pin.toCharArray())

    private suspend fun activate(pin: CharArray): Boolean {
        val result = CompletableDeferred<PinChange>()
        viewModel.activationPinAuth(pin) { result.complete(it) }
        return result.await() == PinChange.SET
    }

    private companion object {
        const val LOCKOUT_MS = 12_000L
    }
}
