package com.pasich.encly.presentation.viewmodel

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.R
import com.pasich.encly.core.common.UiText
import com.pasich.encly.core.security.AuthType
import com.pasich.encly.core.security.AutoLock
import com.pasich.encly.core.security.AutoLockDelay
import com.pasich.encly.core.security.BiometricStatus
import com.pasich.encly.core.security.KeyboardPrivacy
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.pasich.encly.core.security.WipePinChange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
@Suppress("TooManyFunctions") // One small entry point per setting on the Security page.
class SecuritySettingsViewModel @Inject constructor(
    private val securityManager: SecurityManager,
    private val keyboardPrivacy: KeyboardPrivacy,
    private val autoLock: AutoLock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecuritySettingsUiState())
    val uiState: StateFlow<SecuritySettingsUiState> = _uiState.asStateFlow()

    /** The open enrol or disable prompt, if any (see [BiometricPromptGuard]). */
    private val biometricPrompt = BiometricPromptGuard()

    /** An enrol or disable prompt is open; taps on the switch are ignored meanwhile. */
    val biometricInFlight: Boolean get() = biometricPrompt.inFlight

    /** "Strict keyboard privacy" (see KeyboardPrivacy). */
    val strictKeyboard: StateFlow<Boolean> = keyboardPrivacy.strict

    fun setStrictKeyboard(enabled: Boolean) = keyboardPrivacy.setStrict(enabled)

    /** How long the vault stays open after leaving the app (see AutoLock). */
    val autoLockDelay: StateFlow<AutoLockDelay> = autoLock.delay

    fun setAutoLockDelay(delay: AutoLockDelay) = autoLock.setDelay(delay)

    init {
        refresh()
    }

    /** Reloads the vault's security state, e.g. after a recovery phrase was added. */
    fun refresh() {
        viewModelScope.launch {
            val authSettings = withContext(Dispatchers.IO) { securityManager.getSettingsAuth() }
            _uiState.value = _uiState.value.copy(
                isUserCreatedSeedKey = authSettings.isUserCreatedSeedKey,
                authType = authSettings.authType,
                biometricEnable = authSettings.isBiometricEnabled,
                biometricStatus = securityManager.biometricStatus(),
                hasPinSlot = authSettings.hasPinSlot,
                wipePinTurnedOff = authSettings.wipePinTurnedOff,
                loaded = true,
            )
        }
    }

    /**
     * False right after the vault was unlocked with the recovery phrase: the user forgot the
     * PIN, so the new one is set without it.
     */
    fun requiresCurrentPin(): Boolean = !securityManager.canResetPinWithoutCurrent()

    fun pinLockoutRemainingMillis(): Long = securityManager.pinLockoutRemainingMillis()

    /** Checks [pin] against the vault; [pin] is wiped. */
    fun verifyCurrentPin(pin: CharArray, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) {
                try {
                    securityManager.verifyPin(pin)
                } finally {
                    SensitiveDataCleaner.clear(pin)
                }
            }
            if (!ok) {
                _uiState.value = _uiState.value.copy(error = UiText.of(R.string.pin_current_wrong))
            }
            onResult(ok)
        }
    }

    /** Makes [pin] the vault's new PIN; [pin] is wiped. */
    fun activationPinAuth(pin: CharArray, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) {
                try {
                    securityManager.configurePin(pin)
                } finally {
                    SensitiveDataCleaner.clear(pin)
                }
            }
            if (ok) {
                _uiState.value = _uiState.value.copy(authType = AuthType.PIN)
            } else {
                _uiState.value = _uiState.value.copy(error = UiText.of(R.string.pin_update_failed))
            }
            onResult(ok)
        }
    }

    /**
     * Makes [pin] the wipe PIN, replacing any earlier one; [pin] is wiped. Only reached after
     * [verifyCurrentPin] in the same screen.
     */
    fun setWipePin(pin: CharArray, onResult: (WipePinChange) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { securityManager.configureWipePin(pin) }
            if (result == WipePinChange.SET) _uiState.value = _uiState.value.copy(wipePinTurnedOff = false)
            onResult(result)
        }
    }

    /** Turns the wipe PIN off (whether one was set is never known). */
    fun removeWipePin(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { securityManager.removeWipePin() }
            if (ok) _uiState.value = _uiState.value.copy(wipePinTurnedOff = false)
            onResult(ok)
        }
    }

    /**
     * Enabling biometrics performs the auth-bound CryptoObject enrollment itself.
     * Disabling an existing biometric slot requires a fresh strong-biometric confirmation.
     * Taps while either prompt is open are ignored: a second enrolment would replace the key the
     * first one is wrapping.
     */
    fun toggleBiometric(activity: FragmentActivity, enable: Boolean) {
        biometricPrompt.launch(activity) { release ->
            if (enable) enrollBiometric(activity, release) else disableBiometric(activity, release)
        }
    }

    private fun enrollBiometric(activity: FragmentActivity, release: () -> Boolean) {
        securityManager.enrollBiometric(activity) { ok ->
            release()
            _uiState.value = _uiState.value.copy(
                biometricEnable = ok && securityManager.isBiometricEnabled(),
                error = if (ok) null else UiText.of(R.string.biometric_enroll_failed),
            )
        }
    }

    private fun disableBiometric(activity: FragmentActivity, release: () -> Boolean) {
        securityManager.confirmBiometric(activity) { confirmed ->
            release()
            if (confirmed) {
                securityManager.disableBiometric()
                _uiState.value = _uiState.value.copy(biometricEnable = false)
            } else {
                _uiState.value = _uiState.value.copy(
                    biometricEnable = securityManager.isBiometricEnabled(),
                    error = UiText.of(R.string.biometric_change_not_confirmed),
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    data class SecuritySettingsUiState(
        val error: UiText? = null,
        val authType: AuthType = AuthType.NONE,
        val isUserCreatedSeedKey: Boolean = false,
        val biometricEnable: Boolean = false,
        val biometricStatus: BiometricStatus = BiometricStatus.UNAVAILABLE,
        /** A PIN slot exists, so a wipe PIN can be set next to it. */
        val hasPinSlot: Boolean = false,
        /** The PIN key was reset and any wipe PIN with it; Settings asks to set it again. */
        val wipePinTurnedOff: Boolean = false,
        /** The first [refresh] finished; until then the page shows nothing rather than guesses. */
        val loaded: Boolean = false,
    ) {
        val isBiometricAvailable: Boolean get() = biometricStatus == BiometricStatus.AVAILABLE
    }
}
