package com.pasich.encly.presentation.viewmodel

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.security.AuthStrategy
import com.pasich.encly.core.security.BiometricManager
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.pasich.encly.core.security.SessionLockManager
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

/** Outcome of a seed-phrase entry on the lock screen. */
enum class SeedUnlockResult { SUCCESS, WRONG_SEED, DB_ERROR }

/**
 * Backs the app-unlock (lock) screen: PIN verification, biometric prompt and the
 * post-auth database unlock. The heavy work (PBKDF2 PIN hashing and opening the
 * SQLCipher database) runs off the main thread; [busy] drives a loading indicator.
 * Holds no note data and touches no DAO, so it is safe to create while locked.
 */
@HiltViewModel
class LockViewModel @Inject constructor(
    private val securityManager: SecurityManager,
    private val biometricManager: BiometricManager,
    private val sessionLockManager: SessionLockManager
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun strategy(): AuthStrategy = securityManager.authStrategy()

    /** True when the configured strategy unlocks with a seed phrase rather than a PIN. */
    fun isSeedStrategy(): Boolean = strategy() == AuthStrategy.SEED_PHRASE ||
            strategy() == AuthStrategy.SEED_PHRASE_BIOMETRIC

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
            if (result == PinUnlockResult.SUCCESS) sessionLockManager.onUnlocked()
            _busy.value = false
            onResult(result)
        }
    }

    /**
     * Verifies a re-entered seed phrase and, if correct, unlocks the database.
     * The phrase [CharArray] is zeroized as soon as verification finishes.
     */
    fun authenticateSeed(phrase: String, onResult: (SeedUnlockResult) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val chars = phrase.trim().toCharArray()
            val result = withContext(Dispatchers.Default) {
                try {
                    when {
                        !securityManager.verifySeed(chars) -> SeedUnlockResult.WRONG_SEED
                        securityManager.unlockAfterAuth() -> SeedUnlockResult.SUCCESS
                        else -> SeedUnlockResult.DB_ERROR
                    }
                } finally {
                    SensitiveDataCleaner.clear(chars)
                }
            }
            if (result == SeedUnlockResult.SUCCESS) sessionLockManager.onUnlocked()
            _busy.value = false
            onResult(result)
        }
    }

    /** Unlocks the database after a successful biometric auth (off the main thread). */
    fun unlock(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val ok = withContext(Dispatchers.IO) { securityManager.unlockAfterAuth() }
            if (ok) sessionLockManager.onUnlocked()
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
