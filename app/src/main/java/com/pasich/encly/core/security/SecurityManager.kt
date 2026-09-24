package com.pasich.encly.core.security

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import com.pasich.encly.data.database.SecureDatabaseManager
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

enum class InitialStatus {
    NO,
    MAIN,
    ONBOARDING,
    LOSS_DATABASE,
    LOSS_CRYPTO,

    /**
     * An encrypted database without any v2 vault metadata: data written by Encly 1.x, which
     * 2.0 cannot open (there is no migration). Wiped only after an explicit confirmation.
     */
    LEGACY_VAULT,
    AUTH,
    SETUP_AUTH,
}

enum class VaultUnlockResult {
    SUCCESS,
    INVALID_CREDENTIAL,
    DB_ERROR,
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
    private val biometricManager: BiometricManager,
) {
    companion object {
        private const val ONBOARDING_SHOWN_KEY = "onboarding_shown_v2"
        private const val DEK_LENGTH = 32
    }

    @Volatile
    private var sessionDek: ByteArray? = null

    // True while the open session was unlocked with the recovery phrase and no new PIN has
    // been set since. The user proved the vault's strongest secret, so they may replace a
    // forgotten PIN without typing it.
    @Volatile
    private var sessionUnlockedWithRecovery = false

    var securityStatus = InitialStatus.NO

    fun resolveInitialStatus(): InitialStatus {
        val status = initializeSecurity()
        securityStatus = status
        return status
    }

    private fun initializeSecurity(): InitialStatus {
        if (!isOnboardingShown()) return uncommittedVaultStatus()
        if (!seedPhraseManager.verificationKeyData()) return InitialStatus.LOSS_CRYPTO
        if (!secureDatabaseManager.hasEncryptedDatabase()) return InitialStatus.LOSS_DATABASE

        // A lost PIN slot is recoverable only when the user explicitly created a recovery slot.
        // The lock screen can unwrap the DEK with that seed; once unlocked, Settings can create
        // a fresh PIN around the live session DEK.
        if (!authenticationManager.hasPinSlot() && !seedPhraseManager.hasRecoverySeed()) {
            return InitialStatus.LOSS_CRYPTO
        }
        return InitialStatus.AUTH
    }

    /**
     * No committed vault. Onboarding creates a new vault and deletes database.db, so it is only
     * safe when no user data can exist yet. An encrypted database with no vault metadata is a
     * pre-2.0 (v1) vault: 2.0 cannot open it and does not migrate it, so it is only deleted
     * after the explicit confirmation on the older-version screen.
     */
    private fun uncommittedVaultStatus(): InitialStatus = if (secureDatabaseManager.hasEncryptedDatabase() &&
        !seedPhraseManager.hasStoredSeed()
    ) {
        InitialStatus.LEGACY_VAULT
    } else {
        InitialStatus.ONBOARDING
    }

    /** True when first-run setup may (re)create the vault without destroying user data. */
    private fun isSafeToCreateVault(): Boolean =
        !isOnboardingShown() && uncommittedVaultStatus() == InitialStatus.ONBOARDING

    fun authStrategy(): AuthStrategy = authenticationManager.isAuthStrategy()

    fun isBiometricEnabled(): Boolean = authenticationManager.isBiometricEnabled() && biometricManager.hasSlot()

    fun biometricAvailable(): Boolean = biometricManager.isStrongBiometricAvailable()

    fun biometricStatus(): BiometricStatus = biometricManager.strongBiometricStatus()

    fun hasRecoverySeed(): Boolean = seedPhraseManager.hasRecoverySeed()

    fun configurePin(pin: String): Boolean {
        val dek = currentKeyCopy() ?: return false
        val configured = try {
            authenticationManager.configurePin(pin, dek)
        } finally {
            SensitiveDataCleaner.clear(dek)
        }
        if (configured) sessionUnlockedWithRecovery = false
        return configured
    }

    /**
     * Whether a new PIN may be set without the current one: only in a session the recovery
     * phrase unlocked (the PIN was forgotten), until a new PIN is set or the vault locks.
     */
    fun canResetPinWithoutCurrent(): Boolean = sessionUnlockedWithRecovery && sessionDek != null

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
        val opened = try {
            unlockWithRawKey(dek)
        } finally {
            SensitiveDataCleaner.clear(dek)
        }
        sessionUnlockedWithRecovery = opened
        return if (opened) VaultUnlockResult.SUCCESS else VaultUnlockResult.DB_ERROR
    }

    fun requestBiometricKey(activity: FragmentActivity, onResult: (ByteArray?) -> Unit) {
        biometricManager.unlock(activity, onResult)
    }

    fun enrollBiometric(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
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

    fun confirmBiometric(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
        biometricManager.authenticate(
            activity,
            BiometricManager.BiometricType.SETTINGS_TOGGLE,
            object : BiometricManager.BiometricCallback {
                override fun onSuccess() = onResult(true)
                override fun onError(errorCode: Int, errorMessage: String) = onResult(false)
                override fun onFailed() = Unit
                override fun onCancelled() = onResult(false)
            },
        )
    }

    // --- encrypted backups --------------------------------------------------------------

    /** True when backups of this vault can be sealed to its recovery phrase without asking. */
    fun hasBackupKey(): Boolean = seedPhraseManager.hasBackupKey()

    fun isValidRecoveryPhrase(phrase: CharArray): Boolean = seedPhraseManager.isValidMnemonic(phrase)

    /**
     * The seed-derived backup root of the unlocked vault, or null when the session is locked
     * or the vault has no backup-key slot. The caller must wipe the returned key.
     */
    fun copyBackupRootKey(): ByteArray? {
        val dek = sessionDek?.copyOf() ?: return null
        return try {
            seedPhraseManager.unwrapBackupRoot(dek)
        } finally {
            SensitiveDataCleaner.clear(dek)
        }
    }

    /**
     * Adds a recovery seed (and backup key) to the unlocked vault, which then behaves like one
     * created in user-managed mode. See [SeedPhraseManager.addRecoverySeed].
     */
    fun addRecoverySeed(phrase: CharArray): Boolean {
        val dek = sessionDek?.copyOf() ?: return false
        return try {
            seedPhraseManager.addRecoverySeed(phrase, dek)
        } finally {
            SensitiveDataCleaner.clear(dek)
        }
    }

    /** Adds the backup-key slot to an unlocked recovery-seed vault; [phrase] must be its seed. */
    fun createBackupKey(phrase: CharArray): Boolean {
        val dek = sessionDek?.copyOf() ?: return false
        return try {
            seedPhraseManager.createBackupKey(phrase, dek)
        } finally {
            SensitiveDataCleaner.clear(dek)
        }
    }

    fun pinLockoutRemainingMillis(): Long = authenticationManager.remainingLockoutMillis()

    fun verifyPin(pin: String): Boolean = authenticationManager.verifyPinAuth(pin)

    fun verifySeed(phrase: CharArray): Boolean = seedPhraseManager.verifyMnemonic(phrase)

    /**
     * Opens SQLCipher with an already unwrapped v2 DEK.
     * Caller retains ownership of [dek] and should wipe it after this call.
     */
    fun unlockWithRawKey(dek: ByteArray, allowCreate: Boolean = false): Boolean {
        if (dek.size != DEK_LENGTH) return false
        val ok = secureDatabaseManager.unlockDatabase(
            SecretKeySpec(dek, "AES"),
            allowCreate = allowCreate,
        )
        if (ok) {
            setSessionKey(dek)
            securityStatus = InitialStatus.MAIN
        }
        return ok
    }

    /**
     * Completes first-run setup only after a PIN slot exists and the bootstrap DEK opens SQLCipher.
     */
    fun finishInitialSetup(): Boolean = openInitialVault() && commitInitialSetup()

    /**
     * First half of [finishInitialSetup]: opens (creating) the new vault with the bootstrap DEK
     * once a PIN slot exists, without committing onboarding. Lets a staged restore be imported
     * before the vault counts as set up; until [commitInitialSetup], a killed process simply
     * starts onboarding over.
     */
    fun openInitialVault(): Boolean {
        val dek = if (authenticationManager.hasPinSlot()) {
            seedPhraseManager.copyBootstrapKey()
        } else {
            null
        } ?: return false

        return try {
            unlockWithRawKey(dek, allowCreate = true)
        } finally {
            SensitiveDataCleaner.clear(dek)
        }
    }

    /** Second half of [finishInitialSetup]: commits onboarding and drops the bootstrap DEK. */
    fun commitInitialSetup(): Boolean {
        val finalized = setOnboardingShown()
        if (finalized) seedPhraseManager.clearBootstrapKey()
        return finalized
    }

    fun lock() {
        secureDatabaseManager.reset()
        clearSessionKey()
        securityStatus = InitialStatus.AUTH
    }

    /**
     * Whether backgrounding must close the open vault. Not before onboarding is committed: the
     * only vault open then is the one first-run setup just created, possibly still importing a
     * restore that a re-lock would abort. Setup closes it itself if it finishes in the
     * background (SessionLockManager.onUnlocked) or fails.
     */
    fun isLockable(): Boolean =
        isOnboardingShown() && (authenticationManager.hasPinSlot() || seedPhraseManager.hasRecoverySeed())

    fun isDatabaseUnlocked(): Boolean = secureDatabaseManager.isDatabaseUnlocked()

    fun isOnboardingShow(): Boolean = !isOnboardingShown()

    private fun isOnboardingShown(): Boolean = secureStoragePrefs.getBoolean(ONBOARDING_SHOWN_KEY, false)

    fun setOnboardingShown(): Boolean {
        if (!seedPhraseManager.verificationKeyData() || !authenticationManager.hasPinSlot()) {
            return false
        }
        return secureStoragePrefs.edit()
            .putBoolean(ONBOARDING_SHOWN_KEY, true)
            .commit()
    }

    fun generateMnemonicCode(): CharArray = seedPhraseManager.generateMnemonic().chars

    /**
     * Starts a brand-new vault with no legacy state. First-run setup is intentionally
     * destructive until onboarding has been finalized, because no user data is considered
     * committed before a valid PIN slot exists.
     */
    fun initializeNewVault(recoverySeed: CharArray?): Boolean {
        // Refuse to overwrite a committed vault or a database nothing can open yet.
        if (!isSafeToCreateVault()) return false
        secureDatabaseManager.wipe()
        clearSessionKey()
        biometricManager.disable()
        authenticationManager.wipe()
        seedPhraseManager.wipe()
        secureStoragePrefs.edit().remove(ONBOARDING_SHOWN_KEY).commit()
        securityStatus = InitialStatus.ONBOARDING
        return seedPhraseManager.initializeVault(recoverySeed)
    }

    private fun currentKeyCopy(): ByteArray? = sessionDek?.copyOf() ?: seedPhraseManager.copyBootstrapKey()

    private fun setSessionKey(dek: ByteArray) {
        clearSessionKey()
        sessionDek = dek.copyOf()
    }

    private fun clearSessionKey() {
        sessionDek?.let(SensitiveDataCleaner::clear)
        sessionDek = null
        sessionUnlockedWithRecovery = false
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
        isUserCreatedSeedKey = seedPhraseManager.hasRecoverySeed(),
    )
}

data class AuthSettings(val authType: AuthType, val isBiometricEnabled: Boolean, val isUserCreatedSeedKey: Boolean)
