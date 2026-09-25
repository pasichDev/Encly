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
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * BACKGROUNDED: the credential was right, but the app left the foreground before the vault
 * finished opening, so SessionLockManager closed it again. The lock screen stays; no error.
 * KEY_LOST: the device-bound PIN key is gone; only the recovery phrase (or fingerprint) unlocks.
 */
enum class PinUnlockResult { SUCCESS, WRONG_PIN, LOCKED_OUT, KEY_LOST, DB_ERROR, BACKGROUNDED }
enum class SeedUnlockResult { SUCCESS, WRONG_SEED, DB_ERROR, BACKGROUNDED }

@HiltViewModel
@Suppress("TooManyFunctions") // One small entry point per unlock factor and capability.
class LockViewModel @Inject constructor(
    private val securityManager: SecurityManager,
    private val sessionLockManager: SessionLockManager,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** The open biometric prompt, if any (see [BiometricPromptGuard]). */
    private val biometricPrompt = BiometricPromptGuard()

    /** A biometric prompt is open; a new request is ignored meanwhile. */
    val biometricInFlight: Boolean get() = biometricPrompt.inFlight

    fun strategy(): AuthStrategy = securityManager.authStrategy()

    fun isSeedStrategy(): Boolean = strategy() == AuthStrategy.SEED_PHRASE ||
        strategy() == AuthStrategy.SEED_PHRASE_BIOMETRIC

    fun biometricEnabled(): Boolean = securityManager.isBiometricEnabled()
    fun biometricAvailable(): Boolean = securityManager.biometricAvailable()
    fun recoveryAvailable(): Boolean = securityManager.hasRecoverySeed()
    fun lockoutRemainingMillis(): Long = securityManager.pinLockoutRemainingMillis()

    /** Whether the session is closed (again); an unlock reveal then must not open Home. */
    fun isSessionLocked(): Boolean = sessionLockManager.locked.value

    /** Unlocks with [pin], which is wiped. */
    fun authenticatePin(pin: CharArray, onResult: (PinUnlockResult) -> Unit) {
        launchUnlock(onResult) {
            val result = withContext(Dispatchers.Default) {
                when (securityManager.unlockWithPin(pin)) {
                    VaultUnlockResult.SUCCESS -> PinUnlockResult.SUCCESS
                    VaultUnlockResult.INVALID_CREDENTIAL -> PinUnlockResult.WRONG_PIN
                    VaultUnlockResult.LOCKED_OUT -> PinUnlockResult.LOCKED_OUT
                    VaultUnlockResult.PIN_KEY_LOST -> PinUnlockResult.KEY_LOST
                    VaultUnlockResult.DB_ERROR -> PinUnlockResult.DB_ERROR
                }
            }
            publish(result == PinUnlockResult.SUCCESS, result, PinUnlockResult.BACKGROUNDED)
        }
    }

    /** Unlocks with the recovery words (lower case, single spaces); [chars] is wiped afterwards. */
    fun authenticateSeed(chars: CharArray, onResult: (SeedUnlockResult) -> Unit) {
        launchUnlock(onResult) {
            val result = withContext(Dispatchers.Default) {
                try {
                    when (securityManager.unlockWithSeed(chars)) {
                        VaultUnlockResult.SUCCESS -> SeedUnlockResult.SUCCESS
                        VaultUnlockResult.INVALID_CREDENTIAL -> SeedUnlockResult.WRONG_SEED
                        else -> SeedUnlockResult.DB_ERROR
                    }
                } finally {
                    SensitiveDataCleaner.clear(chars)
                }
            }
            publish(result == SeedUnlockResult.SUCCESS, result, SeedUnlockResult.BACKGROUNDED)
        }
    }

    /**
     * Unlocks with the biometric slot. Ignored while a prompt is open; an answer that arrives
     * after the prompt's activity was destroyed (the screen it would report to is gone) is dropped
     * and its key wiped.
     */
    fun authenticateBiometric(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
        biometricPrompt.launch(activity) { release ->
            securityManager.requestBiometricKey(activity) { dek ->
                if (!release()) {
                    dek?.let(SensitiveDataCleaner::clear)
                    return@requestBiometricKey
                }
                if (dek == null) {
                    onResult(false)
                    return@requestBiometricKey
                }
                launchUnlock(onResult) {
                    val ok = withContext(Dispatchers.IO) {
                        try {
                            securityManager.unlockWithRawKey(dek)
                        } finally {
                            SensitiveDataCleaner.clear(dek)
                        }
                    }
                    publish(ok, ok, false)
                }
            }
        }
    }

    /**
     * Runs an unlock to its end even if this ViewModel is cleared meanwhile (the KDF and the
     * database open cannot be interrupted anyway). An unlock that completes for a cleared
     * ViewModel has no screen left to navigate into the vault, so it is closed again
     * ([SessionLockManager.onUnlockAbandoned]) instead of staying open in the background.
     */
    private fun <T> launchUnlock(onResult: (T) -> Unit, unlock: suspend UnlockScope.() -> T) {
        val job = viewModelScope.launch {
            val owner = coroutineContext.job
            _busy.value = true
            val result = withContext(NonCancellable) { UnlockScope(owner).unlock() }
            // A cleared screen gets no result: the unlock was already closed again.
            ensureActive()
            _busy.value = false
            onResult(result)
        }
        job.invokeOnCompletion { _busy.value = false }
    }

    /** What an unlock body can see: whether its ViewModel is still alive. */
    private inner class UnlockScope(private val owner: Job) {
        /**
         * The unlock [succeeded]: publish it if the app is in front and the screen still
         * exists, otherwise close the vault again and report [backgrounded].
         */
        fun <T> publish(succeeded: Boolean, result: T, backgrounded: T): T = when {
            !succeeded -> result

            !owner.isActive -> {
                sessionLockManager.onUnlockAbandoned()
                backgrounded
            }

            sessionLockManager.onUnlocked() -> result

            else -> backgrounded
        }
    }
}
