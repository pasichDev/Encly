package com.pasich.encly.core.security

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import cash.z.ecc.android.bip39.Mnemonics.MnemonicCode
import cash.z.ecc.android.bip39.Mnemonics.WordCount
import com.pasich.encly.core.backup.BackupKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encly v2 vault bootstrap and recovery manager.
 *
 * The SQLCipher key is a random 256-bit DEK. A user-managed BIP39 seed never becomes the
 * database key directly: it derives a recovery KEK that wraps the DEK with AES-256-GCM.
 *
 * No legacy seed hashes, local HKDF salts, HMAC mirrors, or device-only master key are kept.
 *
 * A vault with a recovery seed also keeps a backup-key slot: the seed-derived `backupRoot`
 * (see [BackupKeys]) sealed under a sub-key of the DEK. It lets an unlocked session write
 * encrypted backups that the same 12 words decrypt on a fresh install, without asking for
 * the words on every export. The slot opens only with the DEK, i.e. in an unlocked session.
 */
@Singleton
class SeedPhraseManager @Inject constructor(@param:ApplicationContext private val context: Context) {
    companion object {
        private const val PREF_NAME = "encly_vault_v2"
        private const val VERSION_KEY = "vault_version"
        private const val RECOVERY_SLOT_KEY = "recovery_slot"
        private const val RECOVERY_ENABLED_KEY = "recovery_enabled"
        private const val BACKUP_SLOT_KEY = "backup_slot"
        private const val VAULT_VERSION = 2
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12
        private const val DEK_LENGTH = 32

        private val RECOVERY_SALT = "encly/recovery/salt/v2".toByteArray(Charsets.UTF_8)
        private val RECOVERY_INFO = "encly/recovery/kek/v2".toByteArray(Charsets.UTF_8)
        private val RECOVERY_AAD = "encly/recovery/slot/v2".toByteArray(Charsets.UTF_8)
        private val BACKUP_SLOT_SALT = "encly/backup/slot/salt/v1".toByteArray(Charsets.UTF_8)
        private val BACKUP_SLOT_INFO = "encly/backup/slot/kek/v1".toByteArray(Charsets.UTF_8)
        private val BACKUP_SLOT_AAD = "encly/backup/slot/v1".toByteArray(Charsets.UTF_8)
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    @Volatile
    private var bootstrapDek: ByteArray? = null

    fun generateMnemonic(): MnemonicCode = MnemonicCode(WordCount.COUNT_12)

    fun isValidMnemonic(phrase: CharArray): Boolean = try {
        MnemonicCode(phrase).validate()
        true
    } catch (_: Exception) {
        false
    }

    /**
     * Creates a brand-new vault. Existing v1/v2 metadata is intentionally replaced.
     *
     * [recoverySeed] == null means auto-managed mode: the DEK can only be persisted once the
     * mandatory PIN slot is created. If setup is interrupted before then, onboarding restarts.
     */
    @Suppress("ReturnCount") // Invalid seed and failed durable metadata commits abort setup immediately.
    @Synchronized
    fun initializeVault(recoverySeed: CharArray?): Boolean {
        if (recoverySeed != null && !isValidMnemonic(recoverySeed)) return false

        clearBootstrapKey()
        prefs.edit().clear().commit()

        val dek = ByteArray(DEK_LENGTH).also { SecureRandom().nextBytes(it) }
        return try {
            val recoverySlot = recoverySeed?.let { seed ->
                val kek = deriveRecoveryKek(seed)
                try {
                    wrapDek(dek, kek, RECOVERY_AAD)
                } finally {
                    SensitiveDataCleaner.clear(kek)
                }
            }
            val backupSlot = recoverySeed?.let { seed -> sealBackupRoot(seed, dek) }

            val editor = prefs.edit()
                .putInt(VERSION_KEY, VAULT_VERSION)
                .putBoolean(RECOVERY_ENABLED_KEY, recoverySlot != null)

            if (recoverySlot != null) {
                editor.putString(
                    RECOVERY_SLOT_KEY,
                    Base64.encodeToString(recoverySlot, Base64.NO_WRAP),
                )
            } else {
                editor.remove(RECOVERY_SLOT_KEY)
            }
            if (backupSlot != null) {
                editor.putString(BACKUP_SLOT_KEY, Base64.encodeToString(backupSlot, Base64.NO_WRAP))
            } else {
                editor.remove(BACKUP_SLOT_KEY)
            }

            if (!editor.commit()) return false

            bootstrapDek = dek.copyOf()
            true
        } catch (_: Exception) {
            prefs.edit { clear() }
            false
        } finally {
            SensitiveDataCleaner.clear(dek)
        }
    }

    fun hasStoredSeed(): Boolean = prefs.getInt(VERSION_KEY, 0) == VAULT_VERSION

    fun hasRecoverySeed(): Boolean = hasStoredSeed() &&
        prefs.getBoolean(RECOVERY_ENABLED_KEY, false) &&
        !prefs.getString(RECOVERY_SLOT_KEY, null).isNullOrBlank()

    fun verificationKeyData(): Boolean {
        if (!hasStoredSeed()) return false
        val recoveryEnabled = prefs.getBoolean(RECOVERY_ENABLED_KEY, false)
        return !recoveryEnabled || hasRecoverySeed()
    }

    fun isUserManuallyCreatedKeyByDecryption(): Boolean = hasRecoverySeed()

    @Synchronized
    fun copyBootstrapKey(): ByteArray? = bootstrapDek?.copyOf()

    @Synchronized
    fun clearBootstrapKey() {
        bootstrapDek?.let(SensitiveDataCleaner::clear)
        bootstrapDek = null
    }

    /**
     * Attempts to unwrap the v2 DEK using the BIP39 recovery seed.
     * The returned key belongs to the caller and must be zeroized after use.
     */
    @Suppress("ReturnCount") // Fail-closed early exits keep malformed recovery metadata out of crypto operations.
    fun unlockWithSeed(phrase: CharArray): ByteArray? {
        if (!hasRecoverySeed() || !isValidMnemonic(phrase)) return null
        val encoded = prefs.getString(RECOVERY_SLOT_KEY, null) ?: return null
        val wrapped = try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (_: Exception) {
            return null
        }

        val kek = deriveRecoveryKek(phrase)
        return try {
            unwrapDek(wrapped, kek, RECOVERY_AAD)
        } catch (_: Exception) {
            null
        } finally {
            SensitiveDataCleaner.clear(kek)
            SensitiveDataCleaner.clear(wrapped)
        }
    }

    fun verifyMnemonic(phrase: CharArray): Boolean {
        val dek = unlockWithSeed(phrase) ?: return false
        SensitiveDataCleaner.clear(dek)
        return true
    }

    fun hasBackupKey(): Boolean = hasRecoverySeed() && !prefs.getString(BACKUP_SLOT_KEY, null).isNullOrBlank()

    /**
     * Opens the backup-key slot with the live [dek]. The returned `backupRoot` belongs to the
     * caller and must be zeroized after use.
     */
    @Suppress("ReturnCount") // Fail-closed early exits keep malformed metadata out of crypto.
    fun unwrapBackupRoot(dek: ByteArray): ByteArray? {
        if (!hasBackupKey() || dek.size != DEK_LENGTH) return null
        val encoded = prefs.getString(BACKUP_SLOT_KEY, null) ?: return null
        val wrapped = try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (_: Exception) {
            return null
        }
        val kek = deriveBackupSlotKek(dek)
        return try {
            unwrapDek(wrapped, kek, BACKUP_SLOT_AAD)
        } catch (_: Exception) {
            null
        } finally {
            SensitiveDataCleaner.clear(kek)
            SensitiveDataCleaner.clear(wrapped)
        }
    }

    /**
     * Adds the backup-key slot to a recovery-seed vault that lacks one. [phrase] must be this
     * vault's recovery seed: it has to unwrap the recovery slot to exactly [dek].
     */
    @Synchronized
    fun createBackupKey(phrase: CharArray, dek: ByteArray): Boolean {
        if (!isSeedOfVault(phrase, dek)) return false
        val slot = sealBackupRoot(phrase, dek)
        return try {
            prefs.edit()
                .putString(BACKUP_SLOT_KEY, Base64.encodeToString(slot, Base64.NO_WRAP))
                .commit()
        } finally {
            SensitiveDataCleaner.clear(slot)
        }
    }

    /** True when [phrase] unwraps the recovery slot to exactly [dek]. */
    private fun isSeedOfVault(phrase: CharArray, dek: ByteArray): Boolean {
        val recovered = unlockWithSeed(phrase) ?: return false
        return try {
            MessageDigest.isEqual(recovered, dek)
        } finally {
            SensitiveDataCleaner.clear(recovered)
        }
    }

    /**
     * Turns an unlocked automatic-security vault into a recovery-seed vault: seals the live
     * [dek] under [phrase] (recovery slot) and adds the backup-key slot. The PIN and biometric
     * slots are untouched. Refuses when a recovery seed already exists.
     */
    @Suppress("ReturnCount") // Fail-closed preconditions before any metadata is written.
    @Synchronized
    fun addRecoverySeed(phrase: CharArray, dek: ByteArray): Boolean {
        if (!hasStoredSeed() || hasRecoverySeed()) return false
        if (dek.size != DEK_LENGTH || !isValidMnemonic(phrase)) return false
        val kek = deriveRecoveryKek(phrase)
        val recoverySlot = try {
            wrapDek(dek, kek, RECOVERY_AAD)
        } finally {
            SensitiveDataCleaner.clear(kek)
        }
        val backupSlot = sealBackupRoot(phrase, dek)
        return try {
            prefs.edit()
                .putString(RECOVERY_SLOT_KEY, Base64.encodeToString(recoverySlot, Base64.NO_WRAP))
                .putString(BACKUP_SLOT_KEY, Base64.encodeToString(backupSlot, Base64.NO_WRAP))
                .putBoolean(RECOVERY_ENABLED_KEY, true)
                .commit()
        } finally {
            SensitiveDataCleaner.clear(recoverySlot)
            SensitiveDataCleaner.clear(backupSlot)
        }
    }

    private fun sealBackupRoot(seed: CharArray, dek: ByteArray): ByteArray {
        val root = BackupKeys.rootFromMnemonic(seed)
        val kek = deriveBackupSlotKek(dek)
        return try {
            wrapDek(root, kek, BACKUP_SLOT_AAD)
        } finally {
            SensitiveDataCleaner.clear(root)
            SensitiveDataCleaner.clear(kek)
        }
    }

    /** A dedicated sub-key, so the SQLCipher key itself is never used as an AES-GCM key. */
    private fun deriveBackupSlotKek(dek: ByteArray): ByteArray =
        Hkdf.sha256(ikm = dek, salt = BACKUP_SLOT_SALT, info = BACKUP_SLOT_INFO, length = DEK_LENGTH)

    private fun deriveRecoveryKek(seed: CharArray): ByteArray {
        val normalized = String(seed).trim().lowercase()
        val seedBytes = normalized.toByteArray(Charsets.UTF_8)
        val seedHash = MessageDigest.getInstance("SHA-256").digest(seedBytes)
        SensitiveDataCleaner.clear(seedBytes)
        return try {
            deriveRecoveryKekFromHash(seedHash)
        } finally {
            SensitiveDataCleaner.clear(seedHash)
        }
    }

    private fun deriveRecoveryKekFromHash(seedHash: ByteArray): ByteArray = Hkdf.sha256(
        ikm = seedHash,
        salt = RECOVERY_SALT,
        info = RECOVERY_INFO,
        length = DEK_LENGTH,
    )

    private fun wrapDek(dek: ByteArray, kek: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"))
        cipher.updateAAD(aad)
        val encrypted = cipher.doFinal(dek)
        val iv = cipher.iv
        return ByteArray(iv.size + encrypted.size).also { combined ->
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        }
    }

    private fun unwrapDek(wrapped: ByteArray, kek: ByteArray, aad: ByteArray): ByteArray? {
        if (wrapped.size <= IV_LENGTH) return null
        val iv = wrapped.copyOfRange(0, IV_LENGTH)
        val ciphertext = wrapped.copyOfRange(IV_LENGTH, wrapped.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(kek, "AES"),
                GCMParameterSpec(GCM_TAG_LENGTH, iv),
            )
            cipher.updateAAD(aad)
            cipher.doFinal(ciphertext)
        } finally {
            SensitiveDataCleaner.clear(iv)
            SensitiveDataCleaner.clear(ciphertext)
        }
    }

    fun wipe() {
        clearBootstrapKey()
        prefs.edit { clear() }
    }
}
