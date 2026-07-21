package com.pasich.encly.presentation.viewmodel

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import com.pasich.encly.core.security.AuthStrategy
import com.pasich.encly.core.security.BiometricManager
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.authenticateWithBiometric
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Backs the app-unlock (lock) screen: PIN verification, biometric prompt and the
 * post-auth database unlock. Holds no note data and touches no DAO, so it is safe
 * to create while the database is still locked.
 */
@HiltViewModel
class LockViewModel @Inject constructor(
    private val securityManager: SecurityManager,
    private val biometricManager: BiometricManager
) : ViewModel() {

    fun strategy(): AuthStrategy = securityManager.authStrategy()

    fun biometricEnabled(): Boolean = securityManager.isBiometricEnabled()

    fun biometricAvailable(): Boolean = biometricManager.isStrongBiometricAvailable()

    fun lockoutRemainingMillis(): Long = securityManager.pinLockoutRemainingMillis()

    fun verifyPin(pin: String): Boolean = securityManager.verifyPin(pin)

    /** Unlocks the encrypted database after a successful authentication. */
    fun unlock(): Boolean = securityManager.unlockAfterAuth()

    /** Runs a biometric prompt; [onResult] is invoked with true only on success. */
    fun authenticateBiometric(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
        activity.authenticateWithBiometric(
            biometricManager,
            BiometricManager.BiometricType.APP_UNLOCK,
            onSuccess = { onResult(true) },
            onError = { onResult(false) },
            onCancelled = { onResult(false) }
        )
    }
}
