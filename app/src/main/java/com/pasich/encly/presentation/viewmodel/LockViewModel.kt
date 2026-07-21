package com.pasich.encly.presentation.viewmodel

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.security.AuthStrategy
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

/** Outcome of a PIN entry on the lock screen. */
enum class PinUnlockResult { SUCCESS, WRONG_PIN, DB_ERROR }

/**
 * Backs the app-unlock (lock) screen: PIN verification, biometric prompt and the
 * post-auth database unlock. The heavy work (PBKDF2 PIN hashing and opening the
 * SQLCipher database) runs off the main thread; [busy] drives a loading indicator.
 * Holds no note data and touches no DAO, so it is safe to create while locked.
 */
@HiltViewModel
class LockViewModel @Inject constructor(
    private val securityManager: SecurityManager,
    private val biometricManager: BiometricManager
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun strategy(): AuthStrategy = securityManager.authStrategy()

    fun biometricEnabled(): Boolean = securityManager.isBiometricEnabled()

    fun biometricAvailable(): Boolean = biometricManager.isStrongBiometricAvailable()

    fun lockoutRemainingMillis(): Long = securityManager.pinLockoutRemainingMillis()

    /** Verifies the PIN and, if correct, unlocks the database — all off the main thread. */
    fun authenticatePin(pin: String, onResult: (PinUnlockResult) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val result = withContext(Dispatchers.Default) {
                when {
                    !securityManager.verifyPin(pin) -> PinUnlockResult.WRONG_PIN
                    securityManager.unlockAfterAuth() -> PinUnlockResult.SUCCESS
                    else -> PinUnlockResult.DB_ERROR
                }
            }
            _busy.value = false
            onResult(result)
        }
    }

    /** Unlocks the database after a successful biometric auth (off the main thread). */
    fun unlock(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val ok = withContext(Dispatchers.IO) { securityManager.unlockAfterAuth() }
            _busy.value = false
            onResult(ok)
        }
    }

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
