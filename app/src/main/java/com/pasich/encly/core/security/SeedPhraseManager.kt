package com.pasich.encly.core.security

import cash.z.ecc.android.bip39.Mnemonics.MnemonicCode
import cash.z.ecc.android.bip39.Mnemonics.WordCount
import com.pasich.encly.core.backup.BackupKeys
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encly vault bootstrap and recovery manager.
 *
 * The SQLCipher key is a random 256-bit DEK. A user-managed BIP39 seed never becomes the
 * database key directly: it derives a recovery KEK that wraps the DEK with AES-256-GCM.
 *
 * No seed hashes, HMAC mirrors or device-only master key are kept.
 *
 * A vault with a recovery seed also keeps a backup-key slot: the seed-derived `backupRoot`
 * (see [BackupKeys]) sealed under a sub-key of the DEK. It lets an unlocked session write
 * encrypted backups that the same 12 words decrypt on a fresh install, without asking for
 * the words on every export. The slot opens only with the DEK, i.e. in an unlocked session.
 *
 * Both slots live in the [VaultStore] and are always written together, in one atomic edit.
 */
@Singleton
class SeedPhraseManager @Inject constructor(private val store: VaultStore) {
    companion object {
        private const val VAULT_PREFIX = "vault."
        private const val RECOVERY_PREFIX = "recovery."
        private const val BACKUP_PREFIX = "backup."
        private const val VERSION_KEY = "vault.version"
        private const val RECOVERY_SLOT_KEY = "recovery.slot"
        private const val RECOVERY_ENABLED_KEY = "recovery.enabled"
        private const val BACKUP_SLOT_KEY = "backup.slot"
        private const val VAULT_VERSION = 3
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12
        private const val DEK_LENGTH = 32

        private val RECOVERY_SALT = "encly/recovery/salt/v3".toByteArray(Charsets.UTF_8)
        private val RECOVERY_INFO = "encly/recovery/kek/v3".toByteArray(Charsets.UTF_8)
        private val RECOVERY_AAD = "encly/recovery/slot/v3".toByteArray(Charsets.UTF_8)
        private val BACKUP_SLOT_SALT = "encly/backup/slot/salt/v1".toByteArray(Charsets.UTF_8)
        private val BACKUP_SLOT_INFO = "encly/backup/slot/kek/v1".toByteArray(Charsets.UTF_8)
        private val BACKUP_SLOT_AAD = "encly/backup/slot/v1".toByteArray(Charsets.UTF_8)
    }

    @Volatile
    private var bootstrapDek: ByteArray? = null

    fun generateMnemonic(): MnemonicCode = MnemonicCode(WordCount.COUNT_12)

    fun isValidMnemonic(phrase: CharArray): Boolean {
        val normalized = Mnemonic.normalize(phrase)
        return try {
            MnemonicCode(normalized).validate()
            true
        } catch (_: Exception) {
            false
        } finally {
            SensitiveDataCleaner.clear(normalized)
        }
    }

    /**
     * Creates a brand-new vault. Existing vault metadata is intentionally replaced.
     *
     * [recoverySeed] == null means auto-managed mode: the DEK can only be persisted once the
     * mandatory PIN slot is created. If setup is interrupted before then, onboarding restarts.
     */
    @Suppress("ReturnCount") // Invalid seed and failed durable metadata commits abort setup immediately.
    @Synchronized
    fun initializeVault(recoverySeed: CharArray?): Boolean {
        if (recoverySeed != null && !isValidMnemonic(recoverySeed)) return false

        clearBootstrapKey()
        if (!store.edit { clearOwnKeys() }) return false

        val dek = ByteArray(DEK_LENGTH).also { SecureRandom().nextBytes(it) }
        val recoverySlot = recoverySeed?.let { sealRecoverySlot(it, dek) }
        val backupSlot = recoverySeed?.let { sealBackupRoot(it, dek) }
        return try {
            val committed = store.edit {
                putInt(VERSION_KEY, VAULT_VERSION)
                putBoolean(RECOVERY_ENABLED_KEY, recoverySlot != null)
                recoverySlot?.let { putBytes(RECOVERY_SLOT_KEY, it) }
                backupSlot?.let { putBytes(BACKUP_SLOT_KEY, it) }
            }
            if (committed) bootstrapDek = dek.copyOf()
            committed
        } finally {
            SensitiveDataCleaner.clear(dek)
            recoverySlot?.let(SensitiveDataCleaner::clear)
            backupSlot?.let(SensitiveDataCleaner::clear)
        }
    }

    fun hasStoredSeed(): Boolean = store.getInt(VERSION_KEY, 0) == VAULT_VERSION

    fun hasRecoverySeed(): Boolean = hasStoredSeed() &&
        store.getBoolean(RECOVERY_ENABLED_KEY, false) &&
        store.contains(RECOVERY_SLOT_KEY)

    fun verificationKeyData(): Boolean {
        if (!hasStoredSeed()) return false
        val recoveryEnabled = store.getBoolean(RECOVERY_ENABLED_KEY, false)
        return !recoveryEnabled || hasRecoverySeed()
    }

    @Synchronized
    fun copyBootstrapKey(): ByteArray? = bootstrapDek?.copyOf()

    @Synchronized
    fun clearBootstrapKey() {
        bootstrapDek?.let(SensitiveDataCleaner::clear)
        bootstrapDek = null
    }

    /**
     * Attempts to unwrap the DEK using the BIP39 recovery seed.
     * The returned key belongs to the caller and must be zeroized after use.
     */
    @Suppress("ReturnCount") // Fail-closed early exits keep malformed recovery metadata out of crypto operations.
    fun unlockWithSeed(phrase: CharArray): ByteArray? {
        if (!hasRecoverySeed() || !isValidMnemonic(phrase)) return null
        val wrapped = store.getBytes(RECOVERY_SLOT_KEY) ?: return null
        val kek = deriveRecoveryKek(phrase)
        return try {
            unwrap(wrapped, kek, RECOVERY_AAD)
        } catch (_: GeneralSecurityException) {
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

    fun hasBackupKey(): Boolean = hasRecoverySeed() && store.contains(BACKUP_SLOT_KEY)

    /**
     * Opens the backup-key slot with the live [dek]. The returned `backupRoot` belongs to the
     * caller and must be zeroized after use.
     */
    @Suppress("ReturnCount") // Fail-closed early exits keep malformed metadata out of crypto.
    fun unwrapBackupRoot(dek: ByteArray): ByteArray? {
        if (!hasBackupKey() || dek.size != DEK_LENGTH) return null
        val wrapped = store.getBytes(BACKUP_SLOT_KEY) ?: return null
        val kek = deriveBackupSlotKek(dek)
        return try {
            unwrap(wrapped, kek, BACKUP_SLOT_AAD)
        } catch (_: GeneralSecurityException) {
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
            store.edit { putBytes(BACKUP_SLOT_KEY, slot) }
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
     * slots are untouched. Refuses when a recovery seed already exists (see [replaceRecoverySeed]).
     */
    @Synchronized
    fun addRecoverySeed(phrase: CharArray, dek: ByteArray): Boolean {
        if (!hasStoredSeed() || hasRecoverySeed()) return false
        return writeRecoverySeed(phrase, dek)
    }

    /**
     * Replaces the recovery phrase of an unlocked recovery-seed vault with [newPhrase]: the
     * live [dek] is sealed under the new words and the backup key is re-derived from them, in
     * one atomic write that also drops the old recovery slot. From then on the old words open
     * nothing on this device; backups already made with them still open only with them.
     */
    @Synchronized
    fun replaceRecoverySeed(newPhrase: CharArray, dek: ByteArray): Boolean {
        if (!hasRecoverySeed()) return false
        return writeRecoverySeed(newPhrase, dek)
    }

    private fun writeRecoverySeed(phrase: CharArray, dek: ByteArray): Boolean {
        if (dek.size != DEK_LENGTH || !isValidMnemonic(phrase)) return false
        val recoverySlot = sealRecoverySlot(phrase, dek)
        val backupSlot = sealBackupRoot(phrase, dek)
        return try {
            store.edit {
                putBytes(RECOVERY_SLOT_KEY, recoverySlot)
                putBytes(BACKUP_SLOT_KEY, backupSlot)
                putBoolean(RECOVERY_ENABLED_KEY, true)
            }
        } finally {
            SensitiveDataCleaner.clear(recoverySlot)
            SensitiveDataCleaner.clear(backupSlot)
        }
    }

    private fun sealRecoverySlot(phrase: CharArray, dek: ByteArray): ByteArray {
        val kek = deriveRecoveryKek(phrase)
        return try {
            wrap(dek, kek, RECOVERY_AAD)
        } finally {
            SensitiveDataCleaner.clear(kek)
        }
    }

    private fun sealBackupRoot(seed: CharArray, dek: ByteArray): ByteArray {
        val root = BackupKeys.rootFromMnemonic(seed)
        val kek = deriveBackupSlotKek(dek)
        return try {
            wrap(root, kek, BACKUP_SLOT_AAD)
        } finally {
            SensitiveDataCleaner.clear(root)
            SensitiveDataCleaner.clear(kek)
        }
    }

    /** A dedicated sub-key, so the SQLCipher key itself is never used as an AES-GCM key. */
    private fun deriveBackupSlotKek(dek: ByteArray): ByteArray =
        Hkdf.sha256(ikm = dek, salt = BACKUP_SLOT_SALT, info = BACKUP_SLOT_INFO, length = DEK_LENGTH)

    /**
     * The 12 words carry 128 bits of entropy, so a fast KDF suffices. Normalized exactly like
     * the backup root ([Mnemonic]); different salt/info keep the two keys unrelated.
     */
    private fun deriveRecoveryKek(seed: CharArray): ByteArray {
        val seedHash = Mnemonic.seedHash(seed)
        return try {
            Hkdf.sha256(ikm = seedHash, salt = RECOVERY_SALT, info = RECOVERY_INFO, length = DEK_LENGTH)
        } finally {
            SensitiveDataCleaner.clear(seedHash)
        }
    }

    private fun wrap(plaintext: ByteArray, kek: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"))
        cipher.updateAAD(aad)
        val encrypted = cipher.doFinal(plaintext)
        return try {
            cipher.iv + encrypted
        } finally {
            SensitiveDataCleaner.clear(encrypted)
        }
    }

    private fun unwrap(wrapped: ByteArray, kek: ByteArray, aad: ByteArray): ByteArray? {
        if (wrapped.size <= IV_LENGTH) return null
        val iv = wrapped.copyOfRange(0, IV_LENGTH)
        val ciphertext = wrapped.copyOfRange(IV_LENGTH, wrapped.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            cipher.updateAAD(aad)
            cipher.doFinal(ciphertext)
        } finally {
            SensitiveDataCleaner.clear(iv)
            SensitiveDataCleaner.clear(ciphertext)
        }
    }

    private fun VaultStore.Editor.clearOwnKeys() {
        removePrefix(VAULT_PREFIX)
        removePrefix(RECOVERY_PREFIX)
        removePrefix(BACKUP_PREFIX)
    }

    fun wipe() {
        clearBootstrapKey()
        store.edit { clearOwnKeys() }
    }
}
