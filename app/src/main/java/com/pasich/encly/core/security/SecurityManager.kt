package com.pasich.encly.core.security

import android.content.SharedPreferences
import androidx.fragment.app.FragmentActivity
import com.pasich.encly.core.AppLogger
import com.pasich.encly.data.backup.BackupManager
import com.pasich.encly.data.database.SecureDatabaseManager
import com.pasich.encly.data.handoff.HandoffStagingSweeper
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
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

    /** A PIN lockout is running; the PIN was not checked. */
    LOCKED_OUT,

    /**
     * The device-bound half of the PIN key is gone (Keystore reset, key invalidated): the PIN
     * can no longer unlock on this device. The recovery phrase (or fingerprint) still can.
     */
    PIN_KEY_LOST,
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
    private val appFlags: SharedPreferences,
    private val vaultStore: VaultStore,
    private val seedPhraseManager: SeedPhraseManager,
    private val secureDatabaseManager: SecureDatabaseManager,
    private val authenticationManager: AuthenticationManager,
    private val biometricManager: BiometricManager,
    /** Hilt always passes the real one; the default only spares tests that never erase. */
    private val handoffStaging: HandoffStagingSweeper = HandoffStagingSweeper {},
) {
    companion object {
        private const val TAG = "SecurityManager"
        private const val ONBOARDING_SHOWN_KEY = "onboarding_shown_v3"
        private const val DEK_LENGTH = 32
    }

    @Volatile
    private var sessionDek: ByteArray? = null

    // True while the open session was unlocked with the recovery phrase and no new PIN has
    // been set since. The user proved the vault's strongest secret, so they may replace a
    // forgotten PIN without typing it.
    @Volatile
    private var sessionUnlockedWithRecovery = false

    // Serializes what creates, opens or deletes the database file while a wipe-PIN erase is
    // pending: an unlock that replaces the database, and the erase's finish at startup (which a
    // recreated activity runs again while an unlock may be in the middle of creating the new one).
    private val databaseLock = Any()

    // The erase's finish at startup runs once per process: later calls (a rotated activity, the
    // My Notes hand-off) find the vault already in use.
    private val startupFinished = AtomicBoolean(false)

    // Changes whenever a wipe-PIN erase replaces the database, and never on lock or unlock, so a
    // screen remembered before an erase (the note open when the app re-locked) is not shown in
    // the empty vault. Random per process: one saved by an earlier process never matches.
    private val eraseEpoch = AtomicLong(SecureRandom().nextLong())

    var securityStatus = InitialStatus.NO

    /**
     * Never throws: vault storage that cannot be read (a damaged state file, a storage or
     * Keystore failure) routes to the damaged-vault screen instead of crash-looping the app.
     */
    @Suppress("TooGenericExceptionCaught") // Any startup failure must route, never crash-loop.
    fun resolveInitialStatus(): InitialStatus {
        val status = try {
            initializeSecurity()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Vault state could not be read", e)
            InitialStatus.LOSS_CRYPTO
        }
        securityStatus = status
        return status
    }

    private fun initializeSecurity(): InitialStatus {
        if (vaultStore.isCorrupt()) return InitialStatus.LOSS_CRYPTO
        if (!isOnboardingShown()) return uncommittedVaultStatus()
        // Vaults from before the wipe PIN get their decoy wipe slot; no PIN needed, nothing shown.
        authenticationManager.ensureWipeSlot()
        if (startupFinished.compareAndSet(false, true)) {
            // A key left by an erase the process died in before it was recorded.
            authenticationManager.deleteStrayPinKey()
            // A wipe-PIN erase the process died in is finished before anything else looks at the vault.
            completePendingWipe()
        }
        if (!seedPhraseManager.verificationKeyData()) return InitialStatus.LOSS_CRYPTO
        // An erase that has not created the empty database yet does so at the next PIN unlock.
        if (!secureDatabaseManager.hasEncryptedDatabase() && authenticationManager.pendingWipe() == null) {
            return InitialStatus.LOSS_DATABASE
        }

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

    /** Sets a new PIN slot around the live DEK. [pin] is wiped. */
    fun configurePin(pin: CharArray): Boolean {
        val dek = currentKeyCopy()
        val configured = try {
            dek != null && authenticationManager.configurePin(pin, dek)
        } finally {
            dek?.let(SensitiveDataCleaner::clear)
            SensitiveDataCleaner.clear(pin)
        }
        if (configured) sessionUnlockedWithRecovery = false
        return configured
    }

    /**
     * Whether a new PIN may be set without the current one: only in a session the recovery
     * phrase unlocked (the PIN was forgotten), until a new PIN is set or the vault locks.
     */
    fun canResetPinWithoutCurrent(): Boolean = sessionUnlockedWithRecovery && sessionDek != null

    /**
     * One PIN attempt from the lock screen; [pin] is wiped. The wipe PIN reports
     * [VaultUnlockResult.SUCCESS] like the PIN, with the new, empty vault open: phases 1 and 2
     * of the erase ran in [AuthenticationManager.unlockWithPin], and [unlockWithRawKey] replaces
     * the old database with an empty one. The rest of the erase is left for
     * [completePendingWipe], which the caller runs once the vault is shown (or the next start
     * does), so the wipe PIN does not wait for it.
     */
    fun unlockWithPin(pin: CharArray): VaultUnlockResult {
        val attempt = try {
            authenticationManager.unlockWithPin(pin)
        } finally {
            SensitiveDataCleaner.clear(pin)
        }
        return when (attempt) {
            is PinUnlock.Success -> try {
                if (unlockWithRawKey(attempt.dek)) VaultUnlockResult.SUCCESS else VaultUnlockResult.DB_ERROR
            } finally {
                SensitiveDataCleaner.clear(attempt.dek)
            }

            is PinUnlock.Erased -> try {
                if (unlockWithRawKey(attempt.dek)) VaultUnlockResult.SUCCESS else VaultUnlockResult.DB_ERROR
            } finally {
                SensitiveDataCleaner.clear(attempt.dek)
            }

            PinUnlock.WrongPin -> VaultUnlockResult.INVALID_CREDENTIAL

            PinUnlock.LockedOut -> VaultUnlockResult.LOCKED_OUT

            PinUnlock.KeyLost -> VaultUnlockResult.PIN_KEY_LOST

            PinUnlock.Failed -> VaultUnlockResult.DB_ERROR
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

    /**
     * Replaces the recovery phrase of the unlocked vault with [phrase] (see
     * [SeedPhraseManager.replaceRecoverySeed]). The old words stop opening this vault; backups
     * sealed to them still need them. The caller wipes [phrase].
     */
    fun replaceRecoverySeed(phrase: CharArray): Boolean {
        val dek = sessionDek?.copyOf() ?: return false
        return try {
            seedPhraseManager.replaceRecoverySeed(phrase, dek)
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

    /** Re-authentication with the PIN; counts like an unlock attempt. [pin] is wiped. */
    fun verifyPin(pin: CharArray): Boolean = try {
        authenticationManager.verifyPinAuth(pin)
    } finally {
        SensitiveDataCleaner.clear(pin)
    }

    fun verifySeed(phrase: CharArray): Boolean = seedPhraseManager.verifyMnemonic(phrase)

    /**
     * Opens SQLCipher with an already unwrapped v2 DEK.
     * Caller retains ownership of [dek] and should wipe it after this call.
     *
     * While a wipe-PIN erase still has to create the empty database ([WipeStage.DATABASE]),
     * whatever is left of the old one is deleted first and the new one is created with [dek]:
     * only the new PIN slot can still produce a DEK then. Phase 3 of the erase: the erased slots
     * are already gone, so a kill from here on ends in an empty vault, which the next start or
     * unlock finishes.
     */
    fun unlockWithRawKey(dek: ByteArray, allowCreate: Boolean = false): Boolean {
        if (dek.size != DEK_LENGTH) return false
        return synchronized(databaseLock) {
            val replaceDatabase = authenticationManager.pendingWipe() == WipeStage.DATABASE
            if (replaceDatabase) secureDatabaseManager.wipe()
            val opened = secureDatabaseManager.unlockDatabase(dek, allowCreate = allowCreate || replaceDatabase)
            val ok = opened && (!replaceDatabase || recordErasedDatabase())
            if (ok) {
                setSessionKey(dek)
                securityStatus = InitialStatus.MAIN
            }
            ok
        }
    }

    /**
     * The empty database of an erase was created: records it before anything can be written to
     * it. Unrecorded, the next start would take it for the old database and delete it, with
     * everything added since; so when the store cannot be written the database is closed again
     * and the unlock fails, and the next one creates it anew.
     */
    private fun recordErasedDatabase(): Boolean {
        if (!authenticationManager.markWipeDatabaseCreated()) {
            AppLogger.w(TAG, "The erased vault's new database could not be recorded")
            secureDatabaseManager.reset()
            return false
        }
        eraseEpoch.incrementAndGet()
        return true
    }

    /**
     * Finishes a wipe-PIN erase (see [WipeStage]); does nothing when none is pending. Safe to
     * repeat: every step is a deletion. Runs at startup, and after an unlock that opened the
     * empty database (off the unlock's path, see [unlockWithPin]). Theme, sorting and
     * auto-lock settings are kept, as on any vault.
     */
    fun completePendingWipe() {
        synchronized(databaseLock) {
            val stage = authenticationManager.pendingWipe() ?: return
            // Before the empty database exists, whatever is left of the old one goes (never an open one).
            if (stage == WipeStage.DATABASE) secureDatabaseManager.wipeIfClosed()
            authenticationManager.deleteRetiredPinKey()
            // Its slot went with the erase; this deletes the Keystore key behind it.
            biometricManager.disable()
            appFlags.edit().remove(BackupManager.LAST_EXPORT_KEY).commit()
            // A My Notes hand-off a killed process left in the cache is plaintext of the old vault's time.
            handoffStaging.sweep()
            if (stage == WipeStage.CLEANUP) authenticationManager.clearPendingWipe()
        }
    }

    /**
     * Changes whenever a wipe-PIN erase replaces the database, never on lock or unlock: what was
     * remembered under another value belongs to a vault that is gone.
     */
    fun eraseEpoch(): Long = eraseEpoch.get()

    // --- wipe PIN -----------------------------------------------------------------------

    /**
     * Sets (or replaces) the wipe PIN; only in an unlocked session, after the caller checked the
     * PIN. [pin] is wiped.
     */
    fun configureWipePin(pin: CharArray): WipePinChange = try {
        if (sessionDek == null) WipePinChange.FAILED else authenticationManager.configureWipePin(pin)
    } finally {
        SensitiveDataCleaner.clear(pin)
    }

    /** Turns the wipe PIN off; only in an unlocked session, after the caller checked the PIN. */
    fun removeWipePin(): Boolean = sessionDek != null && authenticationManager.removeWipePin()

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

    private fun isOnboardingShown(): Boolean = appFlags.getBoolean(ONBOARDING_SHOWN_KEY, false)

    fun setOnboardingShown(): Boolean {
        if (!seedPhraseManager.verificationKeyData() || !authenticationManager.hasPinSlot()) {
            return false
        }
        return appFlags.edit()
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
        appFlags.edit().remove(ONBOARDING_SHOWN_KEY).commit()
        // A damaged state file cannot be edited slot by slot; a new vault starts from none.
        if (vaultStore.isCorrupt()) vaultStore.clear()
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
        // Also the way out of a damaged state file, which the per-slot wipes above cannot edit.
        vaultStore.clear()
        appFlags.edit().clear().commit()
        securityStatus = InitialStatus.ONBOARDING
    }

    fun getSettingsAuth(): AuthSettings = AuthSettings(
        authType = authenticationManager.getAuthType(),
        isBiometricEnabled = isBiometricEnabled(),
        isUserCreatedSeedKey = seedPhraseManager.hasRecoverySeed(),
        hasPinSlot = authenticationManager.hasPinSlot(),
        wipePinTurnedOff = authenticationManager.wipePinTurnedOff(),
    )
}

/**
 * The vault's security state as Settings shows it. [wipePinTurnedOff]: the PIN key was reset,
 * so any wipe PIN stopped working (whether one was set is never known without it).
 */
data class AuthSettings(
    val authType: AuthType,
    val isBiometricEnabled: Boolean,
    val isUserCreatedSeedKey: Boolean,
    val hasPinSlot: Boolean = authType == AuthType.PIN,
    val wipePinTurnedOff: Boolean = false,
)
