package com.pasich.encly.core.security

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import com.pasich.encly.data.database.SecureDatabaseManager
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

enum class InitialStatus {
    NO, MAIN, ONBOARDING, LOSS_DATABASE, LOSS_CRYPTO, AUTH, SETUP_AUTH
}

enum class VaultUnlockResult {
    SUCCESS, INVALID_CREDENTIAL, DB_ERROR
}

/**
 * Security coordinator for the v2 vault.
 *
 * The database key is never re-derived from locally stored seed material. Each unlock factor
 * must unwrap the same random DEK, after which the DEK opens SQLCipher.
 */
@Singleton
class SecurityManager @Inject constructor(
    private val secureStoragePrefs: SharedPreferences,
    private val seedPhraseManager: SeedPhraseManager,
    private val secureDatabaseManager: SecureDatabaseManager,
    private val authenticationManager: AuthenticationManager,
    private val biometricManager: BiometricManager
) {
    companion object {
        private const val ONBOARDING_SHOWN_KEY = "onboarding_shown_v2"
        private const val DEK_LENGTH = 32
    }

    @Volatile
    private var sessionDek: ByteArray? = null

    var securityStatus = InitialStatus.NO

    fun resolveInitialStatus(): InitialStatus {
        val status = initializeSecurity()
        securityStatus = status
        return status
    }

    private fun initializeSecurity(): InitialStatus {
        if (!isOnboardingShown()) return InitialStatus.ONBOARDING
        if (!seedPhraseManager.verificationKeyData()) return InitialStatus.LOSS_CRYPTO
        if (!authenticationManager.hasPinSlot()) return InitialStatus.SETUP_AUTH
        return InitialStatus.AUTH
    }

    fun authStrategy(): AuthStrategy = authenticationManager.isAuthStrategy()

    fun isBiometricEnabled(): Boolean =
        authenticationManager.isBiometricEnabled() && biometricManager.hasSlot()

    fun biometricAvailable(): Boolean = biometricManager.isStrongBiometricAvailable()

    fun hasRecoverySeed(): Boolean = seedPhraseManager.hasRecoverySeed()

    fun configurePin(pin: String): Boolean {
        val dek = currentKeyCopy() ?: return false
        return try {
            authenticationManager.configurePin(pin, dek)
        } finally {
            SensitiveDataCleaner.clear(dek)
        }
    }

    fun unlockWithPin(pin: String): VaultUnlockResult {
        val dek = authenticationManager.unlockWithPin(pin)
            ?: return VaultUnlockResult.INVALID_CREDENTIAL
        return try {
            if (unlockWithRawKey(dek)) VaultUnlockResult.SUCCESS else VaultUnlockResult.DB_ERROR
        } finally {
            SensitiveDataCleaner.clear(dek)
        }
    }

    fun unlockWithSeed(phrase: CharArray): VaultUnlockResult {
        val dek = seedPhraseManager.unlockWithSeed(phrase)
            ?: return VaultUnlockResult.INVALID_CREDENTIAL
        return try {
            if (unlockWithRawKey(dek)) VaultUnlockResult.SUCCESS else VaultUnlockResult.DB_ERROR
        } finally {
            SensitiveDataCleaner.clear(dek)
        }
    }

    fun requestBiometricKey(
        activity: FragmentActivity,
        onResult: (ByteArray?) -> Unit
    ) {
        biometricManager.unlock(activity, onResult)
    }

    fun enrollBiometric(
        activity: FragmentActivity,
        onResult: (Boolean) -> Unit
    ) {
        val dek = currentKeyCopy()
        if (dek == null) {
            onResult(false)
            return
        }

        biometricManager.enroll(activity, dek) { ok ->
            authenticationManager.markBiometricEnabled(ok)
            onResult(ok)
        }
        SensitiveDataCleaner.clear(dek)
    }

    fun disableBiometric() {
        biometricManager.disable()
        authenticationManager.markBiometricEnabled(false)
    }

    fun confirmBiometric(
        activity: FragmentActivity,
        onResult: (Boolean) -> Unit
    ) {
        biometricManager.authenticate(
            activity,
            BiometricManager.BiometricType.SETTINGS_TOGGLE,
            object : BiometricManager.BiometricCallback {
                override fun onSuccess() = onResult(true)
                override fun onError(errorCode: Int, errorMessage: String) = onResult(false)
                override fun onFailed() = Unit
                override fun onCancelled() = onResult(false)
            }
        )
    }

    fun pinLockoutRemainingMillis(): Long = authenticationManager.remainingLockoutMillis()

    fun verifyPin(pin: String): Boolean = authenticationManager.verifyPinAuth(pin)

    fun verifySeed(phrase: CharArray): Boolean = seedPhraseManager.verifyMnemonic(phrase)

    /**
     * Opens SQLCipher with an already unwrapped v2 DEK.
     * Caller retains ownership of [dek] and should wipe it after this call.
     */
    fun unlockWithRawKey(dek: ByteArray): Boolean {
        if (dek.size != DEK_LENGTH) return false
        val ok = secureDatabaseManager.unlockDatabase(SecretKeySpec(dek, "AES"))
        if (ok) {
            setSessionKey(dek)
            securityStatus = InitialStatus.MAIN
        }
        return ok
    }

    /**
     * Completes first-run setup only after a PIN slot exists and the bootstrap DEK opens SQLCipher.
     */
    fun finishInitialSetup(): Boolean {
        val dek = if (authenticationManager.hasPinSlot()) {
            seedPhraseManager.copyBootstrapKey()
        } else {
            null
        } ?: return false

        return try {
            val opened = unlockWithRawKey(dek)
            val finalized = opened && setOnboardingShown()
            if (finalized) seedPhraseManager.clearBootstrapKey()
            finalized
        } finally {
            SensitiveDataCleaner.clear(dek)
        }
    }

    fun lock() {
        secureDatabaseManager.reset()
        clearSessionKey()
        securityStatus = InitialStatus.AUTH
    }

    fun isLockable(): Boolean = authenticationManager.hasPinSlot()

    fun isDatabaseUnlocked(): Boolean = secureDatabaseManager.isDatabaseUnlocked()

    fun isOnboardingShow(): Boolean = !isOnboardingShown()

    private fun isOnboardingShown(): Boolean =
        secureStoragePrefs.getBoolean(ONBOARDING_SHOWN_KEY, false)

    fun setOnboardingShown(): Boolean {
        if (!seedPhraseManager.verificationKeyData() || !authenticationManager.hasPinSlot()) {
            return false
        }
        secureStoragePrefs.edit { putBoolean(ONBOARDING_SHOWN_KEY, true) }
        return true
    }

    fun generateMnemonicCode(): CharArray = seedPhraseManager.generateMnemonic().chars

    /**
     * Starts a brand-new vault with no legacy state. First-run setup is intentionally
     * destructive until onboarding has been finalized, because no user data is considered
     * committed before a valid PIN slot exists.
     */
    fun initializeNewVault(recoverySeed: CharArray?): Boolean {
        secureDatabaseManager.wipe()
        clearSessionKey()
        biometricManager.disable()
        authenticationManager.wipe()
        seedPhraseManager.wipe()
        secureStoragePrefs.edit { remove(ONBOARDING_SHOWN_KEY) }
        securityStatus = InitialStatus.ONBOARDING
        return seedPhraseManager.initializeVault(recoverySeed)
    }

    private fun currentKeyCopy(): ByteArray? =
        sessionDek?.copyOf() ?: seedPhraseManager.copyBootstrapKey()

    private fun setSessionKey(dek: ByteArray) {
        clearSessionKey()
        sessionDek = dek.copyOf()
    }

    private fun clearSessionKey() {
        sessionDek?.let(SensitiveDataCleaner::clear)
        sessionDek = null
    }

    fun wipeAndReset() {
        secureDatabaseManager.wipe()
        clearSessionKey()
        biometricManager.disable()
        authenticationManager.wipe()
        seedPhraseManager.wipe()
        secureStoragePrefs.edit { clear() }
        securityStatus = InitialStatus.ONBOARDING
    }

    fun getSettingsAuth(): AuthSettings = AuthSettings(
        authType = authenticationManager.getAuthType(),
        isBiometricEnabled = isBiometricEnabled(),
        isUserCreatedSeedKey = seedPhraseManager.hasRecoverySeed()
    )
}

data class AuthSettings(
    val authType: AuthType,
    val isBiometricEnabled: Boolean,
    val isUserCreatedSeedKey: Boolean
)
