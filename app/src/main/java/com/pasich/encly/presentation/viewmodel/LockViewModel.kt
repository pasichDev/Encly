package com.pasich.encly.presentation.viewmodel

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.security.AuthStrategy
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.core.security.VaultUnlockResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * BACKGROUNDED: the credential was right, but the app left the foreground before the vault
 * finished opening, so SessionLockManager closed it again. The lock screen stays; no error.
 */
enum class PinUnlockResult { SUCCESS, WRONG_PIN, DB_ERROR, BACKGROUNDED }
enum class SeedUnlockResult { SUCCESS, WRONG_SEED, DB_ERROR, BACKGROUNDED }

@HiltViewModel
class LockViewModel @Inject constructor(
    private val securityManager: SecurityManager,
    private val sessionLockManager: SessionLockManager,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    @Volatile
    private var biometricInFlight = false

    fun strategy(): AuthStrategy = securityManager.authStrategy()

    fun isSeedStrategy(): Boolean = strategy() == AuthStrategy.SEED_PHRASE ||
        strategy() == AuthStrategy.SEED_PHRASE_BIOMETRIC

    fun biometricEnabled(): Boolean = securityManager.isBiometricEnabled()
    fun biometricAvailable(): Boolean = securityManager.biometricAvailable()
    fun recoveryAvailable(): Boolean = securityManager.hasRecoverySeed()
    fun lockoutRemainingMillis(): Long = securityManager.pinLockoutRemainingMillis()

    fun authenticatePin(pin: String, onResult: (PinUnlockResult) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val result = withContext(Dispatchers.Default) {
                when (securityManager.unlockWithPin(pin)) {
                    VaultUnlockResult.SUCCESS -> PinUnlockResult.SUCCESS
                    VaultUnlockResult.INVALID_CREDENTIAL -> PinUnlockResult.WRONG_PIN
                    VaultUnlockResult.DB_ERROR -> PinUnlockResult.DB_ERROR
                }
            }
            val published = if (result == PinUnlockResult.SUCCESS && !sessionLockManager.onUnlocked()) {
                PinUnlockResult.BACKGROUNDED
            } else {
                result
            }
            _busy.value = false
            onResult(published)
        }
    }

    /** Unlocks with the recovery words (lower case, single spaces); [chars] is wiped afterwards. */
    fun authenticateSeed(chars: CharArray, onResult: (SeedUnlockResult) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val result = withContext(Dispatchers.Default) {
                try {
                    when (securityManager.unlockWithSeed(chars)) {
                        VaultUnlockResult.SUCCESS -> SeedUnlockResult.SUCCESS
                        VaultUnlockResult.INVALID_CREDENTIAL -> SeedUnlockResult.WRONG_SEED
                        VaultUnlockResult.DB_ERROR -> SeedUnlockResult.DB_ERROR
                    }
                } finally {
                    SensitiveDataCleaner.clear(chars)
                }
            }
            val published = if (result == SeedUnlockResult.SUCCESS && !sessionLockManager.onUnlocked()) {
                SeedUnlockResult.BACKGROUNDED
            } else {
                result
            }
            _busy.value = false
            onResult(published)
        }
    }

    fun authenticateBiometric(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
        if (biometricInFlight) return
        biometricInFlight = true
        securityManager.requestBiometricKey(activity) { dek ->
            biometricInFlight = false
            if (dek == null) {
                onResult(false)
                return@requestBiometricKey
            }
            viewModelScope.launch {
                _busy.value = true
                val ok = withContext(Dispatchers.IO) {
                    try {
                        securityManager.unlockWithRawKey(dek)
                    } finally {
                        SensitiveDataCleaner.clear(dek)
                    }
                }
                val published = ok && sessionLockManager.onUnlocked()
                _busy.value = false
                onResult(published)
            }
        }
    }
}
