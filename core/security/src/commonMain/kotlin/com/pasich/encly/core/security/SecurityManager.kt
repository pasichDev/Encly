package com.pasich.encly.core.security

import com.pasich.encly.core.AppLogger
import com.pasich.encly.core.backup.BackupFlags
import com.pasich.encly.core.crypto.Crypto
import com.pasich.encly.core.platform.KeyValueStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

public enum class InitialStatus {
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

    /**
     * The vault's state file cannot be read right now (an I/O error, not damage): nothing is
     * known about the vault, so nothing may be wiped or created. Retried by asking again.
     */
    STORAGE_UNREADABLE,
}

public enum class VaultUnlockResult {
    SUCCESS,
    INVALID_CREDENTIAL,

    /** A PIN lockout is running; the PIN was not checked. */
    LOCKED_OUT,

    /**
     * The device-bound half of the PIN key is gone (Keystore reset, key invalidated): the PIN
     * can no longer unlock on this device. The recovery phrase (or fingerprint) still can.
     */
    PIN_KEY_LOST,

    /** The store of the device-bound PIN key is locked or not running (a desktop keyring); the attempt was refunded. */
    KEY_STORE_UNAVAILABLE,

    /** The device's key store failed on this attempt (it may pass); the attempt was refunded, the database is fine. */
    KEY_STORE_FAILED,
    DB_ERROR,
}

/**
 * Security coordinator for the v2 vault.
 *
 * The database key is never re-derived from locally stored seed material. Each unlock factor
 * must unwrap the same random DEK, after which the DEK opens SQLCipher.
 */
@OptIn(ExperimentalAtomicApi::class)
@SingleIn(AppScope::class)
@Inject
public class SecurityManager(
    private val appFlags: KeyValueStore,
    private val vaultStore: VaultStore,
    private val seedPhraseManager: SeedPhraseManager,
    private val vaultDatabase: VaultDatabase,
    private val authenticationManager: AuthenticationManager,
    private val biometricManager: BiometricManager,
    /** The graph always passes the real one; the default only spares tests that never erase. */
    private val handoffStaging: HandoffStagingSweeper = HandoffStagingSweeper {},
) {
    public companion object {
        private const val TAG = "SecurityManager"
        private const val ONBOARDING_SHOWN_KEY = "onboarding_shown_v3"

        /** In [VaultStore]: first-run setup is importing a staged restore into the vault it just opened. */
        private const val RESTORE_PENDING_KEY = "setup.restore_pending"
        private const val DEK_LENGTH = 32
    }

    @Volatile
    private var sessionDek: ByteArray? = null

    // Which vault [sessionDek] opens: the real one, or the decoy vault of the duress PIN. Every
    // operation that touches the real vault's key slots is refused in a decoy session.
    @Volatile
    private var sessionVault: VaultKind? = null

    // The vault the last session of this process opened, so a switch moves [eraseEpoch].
    private var lastVault: VaultKind? = null

    // What the open session still owes after an unlock with the recovery phrase (see [RecoveryStage]). While it is
    // not NONE the user proved the vault's strongest secret, so they may replace a forgotten PIN without typing it.
    @Volatile
    private var recoveryStage = RecoveryStage.NONE

    // Serializes what creates, opens or deletes the database file while a wipe-PIN erase is
    // pending: an unlock that replaces the database, and the erase's finish at startup (which a
    // recreated activity runs again while an unlock may be in the middle of creating the new one).
    private val databaseLock = SynchronizedObject()

    // The erase's finish at startup runs once per process: later calls (a rotated activity, the
    // My Notes hand-off) find the vault already in use.
    private val startupFinished = AtomicBoolean(false)

    // Changes whenever a wipe-PIN erase replaces the database or an unlock opens the other vault
    // (real or decoy), and never on a lock and unlock of the same vault, so a screen remembered
    // before (the note open when the app re-locked) is not shown in another vault. Random per
    // process: one saved by an earlier process never matches.
    private val eraseEpoch = AtomicLong(Crypto.randomLong())

    public var securityStatus: InitialStatus = InitialStatus.NO

    /**
     * Never throws: a damaged state file routes to the damaged-vault screen, and storage or Keystore
     * that cannot be read right now routes to the retry screen, instead of crash-looping the app.
     */
    @Suppress("TooGenericExceptionCaught") // Any startup failure must route, never crash-loop.
    public fun resolveInitialStatus(): InitialStatus {
        // A recreated activity (rotation, dark mode, font size) asks again while the vault is
        // open in this process: it stays open. Only a vault that is not open starts locked.
        if (isSessionOpen()) return InitialStatus.MAIN.also { securityStatus = it }
        val status = try {
            initializeSecurity()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Vault state could not be read", e)
            // An unexpected failure is not proof that the vault is damaged, and the damaged-vault screen can only
            // wipe: offer the retry screen. The retry must run the once-per-process startup repair again, which the
            // failed attempt may not have reached.
            startupFinished.store(false)
            InitialStatus.STORAGE_UNREADABLE
        }
        securityStatus = status
        return status
    }

    private fun initializeSecurity(): InitialStatus {
        if (vaultStore.isUnreadable()) return InitialStatus.STORAGE_UNREADABLE
        if (vaultStore.isCorrupt()) return InitialStatus.LOSS_CRYPTO
        if (!isOnboardingShown()) {
            if (!isCommittedWithoutFlag()) return uncommittedVaultStatus()
            // The flag was lost (or its file could not be read): the vault is committed all the same.
            if (!appFlags.commit { putBoolean(ONBOARDING_SHOWN_KEY, true) }) {
                AppLogger.w(TAG, "The onboarding flag of a committed vault could not be repaired")
            }
        } else if (isRestorePending()) {
            // A kill between the commit flag and the marker's removal: the restore had finished. Left in place, the
            // marker would make a later loss of the flag read this committed vault as a half-built one.
            vaultStore.edit { remove(RESTORE_PENDING_KEY) }
        }
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
        if (!vaultDatabase.hasEncryptedDatabase() && authenticationManager.pendingWipe() == null) {
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
    private fun uncommittedVaultStatus(): InitialStatus = if (vaultDatabase.hasEncryptedDatabase() &&
        !seedPhraseManager.hasStoredSeed()
    ) {
        InitialStatus.LEGACY_VAULT
    } else {
        InitialStatus.ONBOARDING
    }

    /**
     * A vault the non-secret flags store forgot: a stored seed, a PIN or recovery slot and the
     * encrypted database (or an erase that is about to replace it) all exist. Setup writes the seed
     * and the slots first and the database last, and holds the bootstrap key in memory until the commit,
     * so an onboarding killed at any earlier step (no seed, no slot, or no database yet: nothing to lose) and
     * the one still
     * running in this process do not match, and restart. A vault marked restore-pending (see [markRestorePending])
     * never matches either: setup was killed after it opened the vault and before the staged backup was imported and
     * committed, so the vault is empty or partial and must be rebuilt, not kept.
     */
    private fun isCommittedWithoutFlag(): Boolean = !isRestorePending() &&
        !seedPhraseManager.hasBootstrapKey() &&
        seedPhraseManager.hasStoredSeed() &&
        (authenticationManager.hasPinSlot() || seedPhraseManager.hasRecoverySeed()) &&
        // A wipe-PIN erase that has not created the empty database yet leaves none: the vault is still committed.
        (vaultDatabase.hasEncryptedDatabase() || authenticationManager.pendingWipe() != null)

    /** True when first-run setup may (re)create the vault without destroying user data. */
    private fun isSafeToCreateVault(): Boolean = !vaultStore.isUnreadable() &&
        !isOnboardingShown() &&
        !isCommittedWithoutFlag() &&
        uncommittedVaultStatus() == InitialStatus.ONBOARDING

    public fun authStrategy(): AuthStrategy = authenticationManager.isAuthStrategy()

    public fun isBiometricEnabled(): Boolean = authenticationManager.isBiometricEnabled() && biometricManager.hasSlot()

    public fun biometricAvailable(): Boolean = biometricManager.isStrongBiometricAvailable()

    /**
     * Whether a re-authentication offers the fingerprint: biometric unlock is on and usable, and the
     * open vault is the real one (a fingerprint belongs to it and never confirms anything in the
     * decoy vault, so the option is not shown there either).
     */
    public fun canReauthWithBiometric(): Boolean = isBiometricEnabled() && biometricAvailable() && !isDecoySession()

    public fun biometricStatus(): BiometricStatus = biometricManager.strongBiometricStatus()

    public fun hasRecoverySeed(): Boolean = seedPhraseManager.hasRecoverySeed()

    /**
     * True while the open session is the decoy vault the duress PIN opened. Nothing on screen
     * may say so: Settings looks the same, and this layer refuses what belongs to the real vault.
     */
    public fun isDecoySession(): Boolean = sessionDek != null && sessionVault == VaultKind.DECOY

    /** Sets a new PIN slot around the live DEK. [pin] is wiped. */
    public fun configurePin(pin: CharArray): Boolean = changePin(pin) == PinChange.SET

    /**
     * [configurePin], telling why it was refused ([PinChange.IN_USE]: the duress PIN). Never in a
     * decoy session: the PIN slot holds the real vault's DEK.
     */
    public fun changePin(pin: CharArray): PinChange {
        if (isDecoySession()) {
            // Refused, but after the same KDF run the real vault does: an instant "no" would tell.
            authenticationManager.spendPinCost(pin)
            SensitiveDataCleaner.clear(pin)
            return PinChange.FAILED
        }
        val dek = currentKeyCopy()
        val change = try {
            if (dek == null) PinChange.FAILED else authenticationManager.changePin(pin, dek)
        } finally {
            dek?.let(SensitiveDataCleaner::clear)
            SensitiveDataCleaner.clear(pin)
        }
        if (change == PinChange.SET) recoveryStage = RecoveryStage.NONE
        return change
    }

    /**
     * Whether a new PIN may be set without the current one: only in a session the recovery
     * phrase unlocked (the PIN was forgotten), until a new PIN is set, kept ([keepUnlockMethodsAfterRecovery])
     * or the vault locks.
     */
    public fun canResetPinWithoutCurrent(): Boolean =
        recoveryStage != RecoveryStage.NONE && sessionDek != null && !isDecoySession()

    /**
     * What the session unlocked with the recovery phrase still has to decide: [RecoveryStage.MUST_SET_PIN]
     * when the PIN can not be used any more (missing, lost key, locked out), [RecoveryStage.CHOOSE] when
     * it can (keep them or set a new PIN), [RecoveryStage.NONE] otherwise.
     */
    public fun recoveryStage(): RecoveryStage = if (sessionDek != null && !isDecoySession()) {
        recoveryStage
    } else {
        RecoveryStage.NONE
    }

    /**
     * "Keep my PIN" after a recovery-phrase unlock that left the choice ([RecoveryStage.CHOOSE]): nothing
     * is changed, and the permission to reset the PIN without the current one ends. False when there was no
     * choice to make (a forced reset cannot be skipped).
     */
    public fun keepUnlockMethodsAfterRecovery(): Boolean {
        if (recoveryStage != RecoveryStage.CHOOSE) return false
        recoveryStage = RecoveryStage.NONE
        return true
    }

    /**
     * One PIN attempt from the lock screen; [pin] is wiped. The wipe PIN reports
     * [VaultUnlockResult.SUCCESS] like the PIN, with the new, empty vault open: phases 1 and 2
     * of the erase ran in [AuthenticationManager.unlockWithPin], and [unlockWithRawKey] replaces
     * the old database with an empty one. The rest of the erase is left for
     * [completePendingWipe], which the caller runs once the vault is shown (or the next start
     * does), so the wipe PIN does not wait for it.
     */
    public fun unlockWithPin(pin: CharArray): VaultUnlockResult {
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

            // The same result as the PIN: nothing tells the two apart on screen.
            is PinUnlock.Decoy -> try {
                if (unlockDecoy(attempt.dek)) VaultUnlockResult.SUCCESS else VaultUnlockResult.DB_ERROR
            } finally {
                SensitiveDataCleaner.clear(attempt.dek)
            }

            PinUnlock.WrongPin -> VaultUnlockResult.INVALID_CREDENTIAL

            PinUnlock.LockedOut -> VaultUnlockResult.LOCKED_OUT

            PinUnlock.KeyLost -> VaultUnlockResult.PIN_KEY_LOST

            PinUnlock.KeyStoreUnavailable -> VaultUnlockResult.KEY_STORE_UNAVAILABLE

            PinUnlock.KeyStoreFailed -> VaultUnlockResult.KEY_STORE_FAILED

            PinUnlock.Failed -> VaultUnlockResult.DB_ERROR
        }
    }

    public fun unlockWithSeed(phrase: CharArray): VaultUnlockResult {
        val dek = seedPhraseManager.unlockWithSeed(phrase)
            ?: return VaultUnlockResult.INVALID_CREDENTIAL
        val opened = try {
            unlockWithRawKey(dek)
        } finally {
            SensitiveDataCleaner.clear(dek)
        }
        if (opened) {
            // The unlock itself did not touch the PIN or the lockout: whether they still serve
            // is decided now, once, and the lock screen routes on it.
            recoveryStage = when (authenticationManager.recoveryFollowUp()) {
                RecoveryFollowUp.KEEP_OR_CHANGE -> RecoveryStage.CHOOSE
                RecoveryFollowUp.MUST_SET_PIN -> RecoveryStage.MUST_SET_PIN
            }
        }
        return if (opened) VaultUnlockResult.SUCCESS else VaultUnlockResult.DB_ERROR
    }

    public fun requestBiometricKey(host: BiometricHost, onResult: (ByteArray?) -> Unit) {
        biometricManager.unlock(host, onResult)
    }

    /**
     * True once after [requestBiometricKey] found the fingerprint slot invalidated by a newly enrolled fingerprint.
     * The slot is gone already; this also records that biometric unlock is off, so the lock screen can say why the
     * fingerprint button went away. Reading it resets it.
     */
    public fun takeBiometricInvalidated(): Boolean {
        if (!biometricManager.takeInvalidated()) return false
        authenticationManager.markBiometricEnabled(false)
        return true
    }

    public fun enrollBiometric(host: BiometricHost, onResult: (Boolean) -> Unit) {
        // The biometric slot opens the real vault only.
        val dek = if (isDecoySession()) null else currentKeyCopy()
        if (dek == null) {
            onResult(false)
            return
        }

        biometricManager.enroll(host, dek) { ok ->
            authenticationManager.markBiometricEnabled(ok)
            onResult(ok)
        }
        SensitiveDataCleaner.clear(dek)
    }

    public fun disableBiometric() {
        if (isDecoySession()) return
        biometricManager.disable()
        authenticationManager.markBiometricEnabled(false)
    }

    public fun confirmBiometric(host: BiometricHost, onResult: (Boolean) -> Unit) {
        // A fingerprint belongs to the real vault; it never confirms anything in the decoy one.
        if (isDecoySession()) {
            onResult(false)
            return
        }
        biometricManager.authenticate(
            host,
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
    public fun hasBackupKey(): Boolean = seedPhraseManager.hasBackupKey()

    public fun isValidRecoveryPhrase(phrase: CharArray): Boolean = seedPhraseManager.isValidMnemonic(phrase)

    /**
     * The seed-derived backup root of the unlocked vault, or null when the session is locked
     * or the vault has no backup-key slot. The caller must wipe the returned key.
     */
    public fun copyBackupRootKey(): ByteArray? {
        val dek = primarySessionKeyCopy() ?: return null
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
    public fun addRecoverySeed(phrase: CharArray): Boolean {
        val dek = primarySessionKeyCopy() ?: return false
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
    public fun replaceRecoverySeed(phrase: CharArray): Boolean {
        val dek = primarySessionKeyCopy() ?: return false
        return try {
            seedPhraseManager.replaceRecoverySeed(phrase, dek)
        } finally {
            SensitiveDataCleaner.clear(dek)
        }
    }

    /** Adds the backup-key slot to an unlocked recovery-seed vault; [phrase] must be its seed. */
    public fun createBackupKey(phrase: CharArray): Boolean {
        val dek = primarySessionKeyCopy() ?: return false
        return try {
            seedPhraseManager.createBackupKey(phrase, dek)
        } finally {
            SensitiveDataCleaner.clear(dek)
        }
    }

    public fun pinLockoutRemainingMillis(): Long = authenticationManager.remainingLockoutMillis()

    /**
     * Re-authentication with the PIN of the open vault (the duress PIN in a decoy session);
     * counts like an unlock attempt. [pin] is wiped.
     */
    public fun verifyPin(pin: CharArray): Boolean = try {
        authenticationManager.verifyPinAuth(pin, decoy = isDecoySession())
    } finally {
        SensitiveDataCleaner.clear(pin)
    }

    public fun verifySeed(phrase: CharArray): Boolean = seedPhraseManager.verifyMnemonic(phrase)

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
    public fun unlockWithRawKey(dek: ByteArray, allowCreate: Boolean = false): Boolean {
        if (dek.size != DEK_LENGTH) return false
        return synchronized(databaseLock) {
            val replaceDatabase = authenticationManager.pendingWipe() == WipeStage.DATABASE
            if (replaceDatabase) vaultDatabase.wipe()
            val opened = vaultDatabase.unlockDatabase(dek, allowCreate = allowCreate || replaceDatabase)
            val ok = opened && (!replaceDatabase || recordErasedDatabase())
            if (ok) {
                setSessionKey(dek, VaultKind.PRIMARY)
                securityStatus = InitialStatus.MAIN
            }
            ok
        }
    }

    /**
     * Opens the decoy vault with its DEK (from the duress slot); the caller keeps and wipes
     * [dek]. Its database is created on the first opening. A decoy database that does not open
     * fails the unlock: it is never deleted and never falls back to the real vault.
     */
    private fun unlockDecoy(dek: ByteArray): Boolean {
        if (dek.size != DEK_LENGTH) return false
        return synchronized(databaseLock) {
            // An erase still in progress replaced the duress slot; nothing may open meanwhile.
            if (authenticationManager.pendingWipe() != null) return@synchronized false
            val create = !vaultDatabase.hasEncryptedDatabase(VaultKind.DECOY)
            val ok = vaultDatabase.unlockDatabase(dek, create, VaultKind.DECOY)
            if (ok) {
                setSessionKey(dek, VaultKind.DECOY)
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
            vaultDatabase.reset()
            return false
        }
        eraseEpoch.addAndFetch(1L)
        return true
    }

    /**
     * Finishes a wipe-PIN erase (see [WipeStage]); does nothing when none is pending. Safe to
     * repeat: every step is a deletion. Runs at startup, and after an unlock that opened the
     * empty database (off the unlock's path, see [unlockWithPin]). Theme, sorting and
     * auto-lock settings are kept, as on any vault.
     */
    public fun completePendingWipe() {
        synchronized(databaseLock) {
            val stage = authenticationManager.pendingWipe() ?: return
            // Before the empty database exists, whatever is left of the old one goes (never an open one).
            if (stage == WipeStage.DATABASE) vaultDatabase.wipeIfClosed()
            authenticationManager.deleteRetiredPinKey()
            // Its slot went with the erase; this deletes the Keystore key behind it.
            biometricManager.disable()
            appFlags.commit { remove(BackupFlags.LAST_EXPORT_KEY) }
            // A My Notes hand-off a killed process left in the cache is plaintext of the old vault's time.
            handoffStaging.sweep()
            if (stage == WipeStage.CLEANUP) authenticationManager.clearPendingWipe()
        }
    }

    /**
     * Changes whenever a wipe-PIN erase replaces the database or the other vault (real or decoy)
     * is opened, never on a lock and unlock of the same vault: what was remembered under another
     * value belongs to a vault that is gone or not open.
     */
    public fun eraseEpoch(): Long = eraseEpoch.load()

    // --- wipe PIN -----------------------------------------------------------------------

    /**
     * Sets (or replaces) the wipe PIN; only in an unlocked session, after the caller checked the
     * PIN. [pin] is wiped.
     */
    public fun configureWipePin(pin: CharArray): WipePinChange = try {
        if (primarySessionOpen()) {
            authenticationManager.configureWipePin(pin)
        } else {
            // In a decoy session the refusal costs the same KDF run as the real vault's set-up.
            if (isDecoySession()) authenticationManager.spendPinCost(pin)
            WipePinChange.FAILED
        }
    } finally {
        SensitiveDataCleaner.clear(pin)
    }

    /** Turns the wipe PIN off; only in an unlocked session, after the caller checked the PIN. */
    public fun removeWipePin(): Boolean = primarySessionOpen() && authenticationManager.removeWipePin()

    // --- duress PIN (decoy vault) ---------------------------------------------------------

    /**
     * Sets (or replaces) the duress PIN; only in a session of the real vault, after the caller
     * checked the PIN. A replaced duress PIN opens the same decoy notes. [pin] is wiped.
     */
    public fun configureDuressPin(pin: CharArray): DuressPinChange {
        val dek = primarySessionKeyCopy()
        return try {
            if (dek == null) {
                // In a decoy session the refusal costs the same KDF run as the real vault's set-up.
                if (isDecoySession()) authenticationManager.spendPinCost(pin)
                DuressPinChange.FAILED
            } else {
                authenticationManager.configureDuressPin(pin, dek) {
                    vaultDatabase.wipeIfClosed(VaultKind.DECOY)
                }
            }
        } finally {
            dek?.let(SensitiveDataCleaner::clear)
            SensitiveDataCleaner.clear(pin)
        }
    }

    /**
     * Turns the duress PIN off and deletes the decoy vault with its notes; only in a session of
     * the real vault, after the caller checked the PIN and the user confirmed. The real vault is
     * not touched. The key goes first: a kill before the files are deleted leaves a database
     * nothing can open, which the next duress PIN set replaces.
     */
    public fun removeDuressPin(): Boolean {
        if (!primarySessionOpen()) return false
        return authenticationManager.removeDuressPin() && vaultDatabase.wipeIfClosed(VaultKind.DECOY)
    }

    /**
     * Whether the real vault has a decoy vault (its duress PIN may have been turned off, see
     * [duressPinTurnedOff]); false outside a session of the real vault.
     */
    public fun hasDuressPin(): Boolean = withPrimaryKey(authenticationManager::hasDecoyVault)

    /** Whether the real vault's duress PIN stopped working; false outside a session of the real vault. */
    public fun duressPinTurnedOff(): Boolean = withPrimaryKey(authenticationManager::duressPinTurnedOff)

    private fun withPrimaryKey(check: (ByteArray) -> Boolean): Boolean {
        val dek = primarySessionKeyCopy() ?: return false
        return try {
            check(dek)
        } finally {
            SensitiveDataCleaner.clear(dek)
        }
    }

    /**
     * Completes first-run setup only after a PIN slot exists and the bootstrap DEK opens SQLCipher.
     */
    public fun finishInitialSetup(): Boolean = openInitialVault() && commitInitialSetup()

    /**
     * Records, durably, that a staged restore is about to be imported into the vault [openInitialVault] creates.
     * Call it before [openInitialVault] when a restore is staged; [commitInitialSetup] clears it. A process killed in
     * between leaves the marker behind, and startup then does not treat that vault as committed (the database may
     * already exist while the import is missing or half done): it deletes it and starts onboarding over.
     * Returns false when the marker could not be written; setup must not go on then.
     */
    public fun markRestorePending(): Boolean = vaultStore.edit { putBoolean(RESTORE_PENDING_KEY, true) }

    private fun isRestorePending(): Boolean = vaultStore.getBoolean(RESTORE_PENDING_KEY, false)

    /**
     * First half of [finishInitialSetup]: opens (creating) the new vault with the bootstrap DEK
     * once a PIN slot exists, without committing onboarding. Lets a staged restore be imported
     * before the vault counts as set up; until [commitInitialSetup], a killed process simply
     * starts onboarding over (a staged restore sets [markRestorePending] first, so that holds once the
     * database exists too).
     */
    public fun openInitialVault(): Boolean {
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
    public fun commitInitialSetup(): Boolean {
        val finalized = setOnboardingShown()
        if (finalized) {
            // After the flag, never before: a kill between the two leaves a flagged vault, which startup trusts.
            if (isRestorePending()) vaultStore.edit { remove(RESTORE_PENDING_KEY) }
            seedPhraseManager.clearBootstrapKey()
        }
        return finalized
    }

    /**
     * Closes the vault and drops the session key. Serialized with the open ([unlockWithRawKey]): a lock never lands
     * between the database being opened and its key being recorded, which would leave a key with no database to
     * close it. It can therefore wait for an open in progress. A database that fails to close still leaves no
     * session key behind; the failure is passed on.
     */
    public fun lock() {
        synchronized(databaseLock) {
            try {
                vaultDatabase.reset()
            } finally {
                // Whatever closing the database did: the session key never outlives the lock.
                clearSessionKey()
                biometricManager.cancelPendingEnrolment()
                securityStatus = InitialStatus.AUTH
            }
        }
    }

    /**
     * Whether backgrounding must close the open vault. Not before onboarding is committed: the
     * only vault open then is the one first-run setup just created, possibly still importing a
     * restore that a re-lock would abort. Setup closes it itself if it finishes in the
     * background (SessionLockManager.onUnlocked) or fails.
     */
    public fun isLockable(): Boolean = (isOnboardingShown() || isCommittedWithoutFlag()) &&
        (authenticationManager.hasPinSlot() || seedPhraseManager.hasRecoverySeed())

    public fun isDatabaseUnlocked(): Boolean = vaultDatabase.isDatabaseUnlocked()

    /** True while this process holds the vault open: the session key and the database. */
    private fun isSessionOpen(): Boolean = sessionDek != null && vaultDatabase.isDatabaseUnlocked()

    public fun isOnboardingShow(): Boolean = !isOnboardingShown()

    private fun isOnboardingShown(): Boolean = appFlags.getBoolean(ONBOARDING_SHOWN_KEY, false)

    public fun setOnboardingShown(): Boolean {
        if (!seedPhraseManager.verificationKeyData() || !authenticationManager.hasPinSlot()) {
            return false
        }
        return appFlags.commit { putBoolean(ONBOARDING_SHOWN_KEY, true) }
    }

    public fun generateMnemonicCode(): CharArray = seedPhraseManager.generateMnemonic()

    /**
     * Starts a brand-new vault with no legacy state. First-run setup is intentionally
     * destructive until onboarding has been finalized, because no user data is considered
     * committed before a valid PIN slot exists.
     */
    public fun initializeNewVault(recoverySeed: CharArray?): Boolean {
        // Refuse to overwrite a committed vault or a database nothing can open yet.
        if (!isSafeToCreateVault()) return false
        vaultDatabase.wipe()
        clearSessionKey()
        biometricManager.disable()
        authenticationManager.wipe()
        seedPhraseManager.wipe()
        appFlags.commit { remove(ONBOARDING_SHOWN_KEY) }
        // A damaged state file cannot be edited slot by slot; a new vault starts from none.
        if (vaultStore.isCorrupt()) vaultStore.clear()
        // The marker of a setup that was killed mid-restore; the new vault has none.
        if (isRestorePending()) vaultStore.edit { remove(RESTORE_PENDING_KEY) }
        securityStatus = InitialStatus.ONBOARDING
        return seedPhraseManager.initializeVault(recoverySeed)
    }

    private fun currentKeyCopy(): ByteArray? = sessionDek?.copyOf() ?: seedPhraseManager.copyBootstrapKey()

    private fun primarySessionOpen(): Boolean = sessionDek != null && sessionVault == VaultKind.PRIMARY

    /** A copy of the real vault's DEK, or null when no session of the real vault is open. */
    private fun primarySessionKeyCopy(): ByteArray? = if (sessionVault ==
        VaultKind.PRIMARY
    ) {
        sessionDek?.copyOf()
    } else {
        null
    }

    private fun setSessionKey(dek: ByteArray, vault: VaultKind) {
        clearSessionKey()
        // What a screen remembered in the other vault must not come back in this one.
        if (lastVault != null && lastVault != vault) eraseEpoch.addAndFetch(1L)
        lastVault = vault
        sessionDek = dek.copyOf()
        sessionVault = vault
    }

    private fun clearSessionKey() {
        sessionDek?.let(SensitiveDataCleaner::clear)
        sessionDek = null
        sessionVault = null
        recoveryStage = RecoveryStage.NONE
    }

    /**
     * Erases everything: both vaults, every key slot. Never from a decoy session, where it
     * changes nothing and returns false.
     *
     * Keys before data, as in the wipe-PIN erase: every slot goes in one atomic write, then the
     * Keystore keys, and only then the database files. A kill before that write leaves the vault
     * as it was; a kill after it leaves no way to a DEK beside whatever is left of the databases,
     * and the next start shows the damaged-vault screen ([InitialStatus.LOSS_CRYPTO]), whose only
     * action is this erase again.
     */
    public fun wipeAndReset(): Boolean {
        if (isDecoySession()) return false
        // A state file that cannot be edited (damaged, or not readable now) is deleted instead.
        if (!vaultStore.eraseEntries()) vaultStore.clear()
        try {
            biometricManager.disable()
            authenticationManager.wipe()
            seedPhraseManager.wipe()
        } finally {
            // Whatever a key deletion throws, the databases still go, and the session key with them.
            try {
                vaultDatabase.wipe()
            } finally {
                clearSessionKey()
            }
        }
        // The empty state file goes too; a new vault starts from none.
        vaultStore.clear()
        appFlags.commit { clear() }
        securityStatus = InitialStatus.ONBOARDING
        return true
    }

    public fun getSettingsAuth(): AuthSettings = AuthSettings(
        authType = authenticationManager.getAuthType(),
        isBiometricEnabled = isBiometricEnabled(),
        isUserCreatedSeedKey = seedPhraseManager.hasRecoverySeed(),
        hasPinSlot = authenticationManager.hasPinSlot(),
        wipePinTurnedOff = authenticationManager.wipePinTurnedOff(),
        hasDuressPin = hasDuressPin(),
        duressPinTurnedOff = duressPinTurnedOff(),
    )
}

/**
 * The vault's security state as Settings shows it. [wipePinTurnedOff]: the PIN key was reset,
 * so any wipe PIN stopped working (whether one was set is never known without it).
 */
public data class AuthSettings(
    val authType: AuthType,
    val isBiometricEnabled: Boolean,
    val isUserCreatedSeedKey: Boolean,
    val hasPinSlot: Boolean = authType == AuthType.PIN,
    val wipePinTurnedOff: Boolean = false,
    /** The real vault has a duress PIN (known only in its own session). */
    val hasDuressPin: Boolean = false,
    /** A new PIN slot turned the duress PIN off; setting it again keeps the notes. */
    val duressPinTurnedOff: Boolean = false,
)

/** What a session unlocked with the recovery phrase still has to decide. */
public enum class RecoveryStage {
    /** Nothing: the session was not unlocked with the phrase, or what it owed is settled. */
    NONE,

    /** The PIN still serves: keep it, or set a new PIN. */
    CHOOSE,

    /** The PIN can not be used (missing, lost key, locked out): a new PIN is set before going on. */
    MUST_SET_PIN,
}
