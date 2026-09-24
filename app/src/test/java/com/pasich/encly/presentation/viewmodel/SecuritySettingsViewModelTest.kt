package com.pasich.encly.presentation.viewmodel

import androidx.fragment.app.FragmentActivity
import com.pasich.encly.R
import com.pasich.encly.core.common.UiText
import com.pasich.encly.core.security.AuthSettings
import com.pasich.encly.core.security.AuthType
import com.pasich.encly.core.security.BiometricStatus
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.testutil.answerCallback
import com.pasich.encly.testutil.anyCallback
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/** Settings → Security: the loaded state, changing the PIN and switching biometrics. */
@OptIn(ExperimentalCoroutinesApi::class)
class SecuritySettingsViewModelTest {
    private lateinit var security: SecurityManager
    private lateinit var viewModel: SecuritySettingsViewModel
    private val activity: FragmentActivity = mock(FragmentActivity::class.java)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        security = mock(SecurityManager::class.java)
        `when`(security.getSettingsAuth()).thenReturn(
            AuthSettings(authType = AuthType.PIN, isBiometricEnabled = true, isUserCreatedSeedKey = false),
        )
        `when`(security.biometricStatus()).thenReturn(BiometricStatus.AVAILABLE)
        viewModel = SecuritySettingsViewModel(security)
        // The first load reads the mock on an IO thread; stubbing it meanwhile would race.
        runBlocking { viewModel.uiState.first { it.loaded } }
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
        `when`(security.verifyPin("111111")).thenReturn(false)
        `when`(security.verifyPin("222222")).thenReturn(true)

        assertFalse(checkCurrent("111111"))
        assertEquals(UiText.of(R.string.pin_current_wrong), viewModel.uiState.value.error)

        viewModel.clearError()
        assertTrue(checkCurrent("222222"))
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun aNewPinIsStoredOrItsFailureShown() = runTest {
        viewModel.uiState.first { it.loaded }
        `when`(security.configurePin("333333")).thenReturn(true)
        `when`(security.configurePin("444444")).thenReturn(false)

        assertTrue(activate("333333"))
        assertEquals(AuthType.PIN, viewModel.uiState.value.authType)
        assertNull(viewModel.uiState.value.error)

        assertFalse(activate("444444"))
        assertEquals(UiText.of(R.string.pin_update_failed), viewModel.uiState.value.error)
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
    fun enablingBiometricsEnrollsAndShowsTheResult() = runTest {
        viewModel.uiState.first { it.loaded }
        answerCallback(true).`when`(security).enrollBiometric(eqValue(activity), anyCallback())
        `when`(security.isBiometricEnabled()).thenReturn(true)

        viewModel.toggleBiometric(activity, enable = true)

        assertTrue(viewModel.uiState.value.biometricEnable)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun aFailedEnrollmentLeavesBiometricsOffWithAMessage() = runTest {
        viewModel.uiState.first { it.loaded }
        answerCallback(false).`when`(security).enrollBiometric(eqValue(activity), anyCallback())

        viewModel.toggleBiometric(activity, enable = true)

        assertFalse(viewModel.uiState.value.biometricEnable)
        assertEquals(UiText.of(R.string.biometric_enroll_failed), viewModel.uiState.value.error)
    }

    @Test
    fun disablingBiometricsNeedsAConfirmedPrompt() = runTest {
        viewModel.uiState.first { it.loaded }
        answerCallback(true).`when`(security).confirmBiometric(eqValue(activity), anyCallback())

        viewModel.toggleBiometric(activity, enable = false)

        verify(security).disableBiometric()
        assertFalse(viewModel.uiState.value.biometricEnable)
    }

    @Test
    fun anUnconfirmedDisableKeepsBiometricsOn() = runTest {
        viewModel.uiState.first { it.loaded }
        answerCallback(false).`when`(security).confirmBiometric(eqValue(activity), anyCallback())
        `when`(security.isBiometricEnabled()).thenReturn(true)

        viewModel.toggleBiometric(activity, enable = false)

        verify(security, never()).disableBiometric()
        assertTrue(viewModel.uiState.value.biometricEnable)
        assertEquals(UiText.of(R.string.biometric_change_not_confirmed), viewModel.uiState.value.error)
    }

    private suspend fun checkCurrent(pin: String): Boolean {
        val result = CompletableDeferred<Boolean>()
        viewModel.verifyCurrentPin(pin) { result.complete(it) }
        return result.await()
    }

    private suspend fun activate(pin: String): Boolean {
        val result = CompletableDeferred<Boolean>()
        viewModel.activationPinAuth(pin) { result.complete(it) }
        return result.await()
    }

    private companion object {
        const val LOCKOUT_MS = 12_000L
    }
}
