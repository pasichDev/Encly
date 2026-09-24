package com.pasich.encly.domain.usecase

import androidx.fragment.app.FragmentActivity
import com.pasich.encly.core.backup.BackupError
import com.pasich.encly.core.backup.BackupException
import com.pasich.encly.core.common.suspendRunCatching
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.pasich.encly.data.backup.BackupManager
import com.pasich.encly.data.backup.PendingRestore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingUseCase @Inject constructor(
    private val securityManager: SecurityManager,
    private val backupManager: BackupManager,
    private val pendingRestore: PendingRestore,
) {
    /** A new 12-word recovery seed. The caller owns the array and must wipe it. */
    fun generateRecoverySeed(): Result<CharArray> = suspendRunCatching { securityManager.generateMnemonicCode() }

    /**
     * Creates a fresh vault. With a [recoverySeed] the user manages recovery themselves and the
     * vault gets a recovery slot for it; with null it is auto-managed and has none.
     * [recoverySeed] is wiped in every case.
     */
    fun createVault(recoverySeed: CharArray?): Result<Unit> = try {
        suspendRunCatching {
            // A fresh vault never carries a backup staged by an abandoned "Restore from backup".
            pendingRestore.clear()
            check(securityManager.initializeNewVault(recoverySeed)) { "Failed to initialize the vault" }
        }
    } finally {
        recoverySeed?.let(SensitiveDataCleaner::clear)
    }

    /**
     * First-run "Restore from backup". Decrypts and validates [file] with the typed recovery
     * phrase first, so a wrong phrase or a damaged file changes nothing. Only then is the new
     * vault created, with that same phrase as its recovery seed (future backups open with the
     * same 12 words), and the payload staged in memory: it is written once the mandatory PIN
     * setup has opened the vault (see [PendingRestore.apply]).
     *
     * Fails with a [BackupException]; [phraseInput] is wiped in every case.
     */
    fun restoreFromBackup(file: ByteArray, phraseInput: CharArray): Result<Unit> {
        val phrase = backupManager.normalizeRecoveryPhrase(phraseInput)
        SensitiveDataCleaner.clear(phraseInput)
        return try {
            val payload = backupManager.decrypt(file, phrase)
            pendingRestore.clear()
            if (!securityManager.initializeNewVault(phrase)) {
                throw BackupException(BackupError.IO)
            }
            pendingRestore.stage(payload)
            Result.success(Unit)
        } catch (e: BackupException) {
            Result.failure(e)
        } finally {
            SensitiveDataCleaner.clear(phrase)
        }
    }

    /** Drops a backup staged by "Restore from backup" when the user leaves that path. */
    fun discardRestore() = pendingRestore.clear()

    /**
     * Sets the PIN slot of the vault just created (not committed yet). [pin] is wiped in every
     * case; the String the PIN hasher needs lives only for this call.
     */
    fun configurePin(pin: CharArray): Boolean = try {
        securityManager.configurePin(String(pin))
    } finally {
        SensitiveDataCleaner.clear(pin)
    }

    fun biometricAvailable(): Boolean = securityManager.biometricAvailable()

    /** Adds the biometric slot to the vault just created; shows the system prompt. */
    fun enrollBiometric(activity: FragmentActivity, onResult: (Boolean) -> Unit) =
        securityManager.enrollBiometric(activity, onResult)
}
