package com.pasich.encly.presentation.viewmodel

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import com.pasich.encly.core.security.AuthenticationManager
import com.pasich.encly.core.security.BiometricManager
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.authenticateWithBiometric
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Backs the mandatory auth-setup screen shown after onboarding: the user must create a
 * PIN and, when the device supports it, enable biometric unlock. It then unlocks the
 * database and enters the app. Touches no note data / DAO (runs before unlock).
 */
@HiltViewModel
class AuthSetupViewModel @Inject constructor(
    private val securityManager: SecurityManager,
    private val authenticationManager: AuthenticationManager,
    private val biometricManager: BiometricManager
) : ViewModel() {

    fun biometricAvailable(): Boolean = biometricManager.isStrongBiometricAvailable()

    /** Stores the chosen PIN. Returns true on success. */
    fun setPin(pin: String): Boolean = authenticationManager.activatePinAuth(pin)

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

    /** Finalizes setup: marks onboarding complete and unlocks the database. */
    fun finishSetup(): Boolean {
        securityManager.setOnboardingShown()
        return securityManager.unlockAfterAuth()
    }
}
