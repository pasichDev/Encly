package com.pasich.encly.data.backup

import android.content.SharedPreferences
import androidx.core.content.edit
import com.pasich.encly.core.AppLogger
import com.pasich.encly.core.backup.BackupCipher
import com.pasich.encly.core.backup.BackupError
import com.pasich.encly.core.backup.BackupException
import com.pasich.encly.core.backup.BackupFormat
import com.pasich.encly.core.backup.BackupHeader
import com.pasich.encly.core.backup.BackupKeys
import com.pasich.encly.core.backup.BackupPayload
import com.pasich.encly.core.backup.BackupPayloadCodec
import com.pasich.encly.core.backup.BackupSecret
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SensitiveDataCleaner
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted export/import of the whole vault. Files are built and read fully in memory and
 * handed to/from the Storage Access Framework by the UI: no temp files, no permissions, no
 * network. Plaintext payload buffers and derived keys are zeroized after use.
 */
@Singleton
class BackupManager @Inject constructor(
    private val securityManager: SecurityManager,
    private val store: VaultDataStore,
    private val secureStoragePrefs: SharedPreferences,
) {
    internal var clock: () -> Long = System::currentTimeMillis

    /** The import limit, which export enforces too; replaceable in tests. */
    internal var maxFileBytes: Int = BackupFormat.MAX_FILE_BYTES

    /**
     * Seals every note (trash included), tag and task of the unlocked vault. Throws
     * [BackupError.TOO_LARGE] instead of producing a file larger than import accepts, which
     * would look like a good backup but could never be restored.
     */
    suspend fun createBackup(): ByteArray {
        val payload = guarded { BackupMapper.toPayload(store.snapshot(), clock()) }
        val root = securityManager.copyBackupRootKey()
            ?: throw BackupException(BackupError.VAULT_UNAVAILABLE)
        val plaintext = BackupPayloadCodec.encode(payload)
        return try {
            if (BackupFormat.fileSizeFor(plaintext.size) > maxFileBytes) {
                throw BackupException(BackupError.TOO_LARGE)
            }
            BackupCipher.seal(plaintext, BackupSecret.RecoveryRoot(root))
        } finally {
            SensitiveDataCleaner.clear(plaintext)
            SensitiveDataCleaner.clear(root)
        }
    }

    /** Structure check only: rejects a wrong or unsupported file before asking for the words. */
    fun inspect(file: ByteArray): BackupHeader = BackupCipher.inspect(file)

    /**
     * Authenticates, decrypts and validates [file] without touching the database.
     * [phrase] must already be normalized ([normalizeRecoveryPhrase]); the caller wipes it.
     */
    fun decrypt(file: ByteArray, phrase: CharArray): BackupPayload {
        if (!securityManager.isValidRecoveryPhrase(phrase)) {
            throw BackupException(BackupError.INVALID_PHRASE)
        }
        val plaintext = BackupCipher.open(file, BackupSecret.RecoveryPhrase(phrase))
        return try {
            BackupPayloadCodec.decode(plaintext)
        } finally {
            SensitiveDataCleaner.clear(plaintext)
        }
    }

    /** Lower-case words separated by single spaces, as stored for the vault's recovery slot. */
    fun normalizeRecoveryPhrase(input: CharArray): CharArray = BackupKeys.normalizeMnemonic(input)

    /** Imports into the unlocked vault in one transaction; nothing is written on failure. */
    suspend fun import(payload: BackupPayload, mode: ImportMode): ImportSummary =
        guarded { BackupImporter.import(payload, mode, store) }

    fun lastExportAt(): Long? = secureStoragePrefs.getLong(LAST_EXPORT_KEY, 0L).takeIf { it > 0L }

    fun recordExport() {
        secureStoragePrefs.edit { putLong(LAST_EXPORT_KEY, clock()) }
    }

    private suspend fun <T> guarded(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: RuntimeException) {
        // Vault closed underneath us (re-lock), a constraint or an I/O failure. The cause is
        // dropped on purpose: SQLite messages can quote row content.
        AppLogger.w(TAG, "Backup operation failed")
        throw BackupException(BackupError.IO)
    }

    private companion object {
        const val TAG = "BackupManager"
        const val LAST_EXPORT_KEY = "backup_last_export_at"
    }
}
