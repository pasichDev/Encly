package com.pasich.encly.presentation.viewmodel

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.security.AuthenticationManager
import com.pasich.encly.core.security.BiometricManager
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.authenticateWithBiometric
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Backs the mandatory auth-setup screen shown after onboarding: the user must create a
 * PIN and, when the device supports it, enable biometric unlock. It then unlocks the
 * database and enters the app. The heavy work (PBKDF2 PIN hashing and opening the
 * SQLCipher database) runs off the main thread; [busy] drives a loading indicator.
 */
@HiltViewModel
class AuthSetupViewModel @Inject constructor(
    private val securityManager: SecurityManager,
    private val authenticationManager: AuthenticationManager,
    private val biometricManager: BiometricManager
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun biometricAvailable(): Boolean = biometricManager.isStrongBiometricAvailable()

    /** Stores the chosen PIN (PBKDF2 hashing runs off the main thread). */
    fun setPin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val ok = withContext(Dispatchers.Default) { authenticationManager.activatePinAuth(pin) }
            _busy.value = false
            onResult(ok)
        }
    }

    /** Runs a biometric prompt and, on success, enables biometric unlock. */
    fun enableBiometric(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
        activity.authenticateWithBiometric(
            biometricManager,
            BiometricManager.BiometricType.SETTINGS_TOGGLE,
            onSuccess = { onResult(authenticationManager.activateBiometricAuth()) },
            onError = { onResult(false) },
            onCancelled = { onResult(false) }
        )
    }

    /** Finalizes setup: marks onboarding complete and unlocks the database (off-thread). */
    fun finishSetup(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val ok = withContext(Dispatchers.IO) {
                securityManager.setOnboardingShown()
                securityManager.unlockAfterAuth()
            }
            _busy.value = false
            onResult(ok)
        }
    }
}
