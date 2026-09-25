package com.pasich.encly.core.backup

import com.pasich.encly.core.security.SensitiveDataCleaner
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * What the per-file key is derived from. Both forms describe the same recovery phrase; the
 * caller keeps ownership of the wrapped arrays and wipes them. [BackupCipher] never retains them.
 */
sealed class BackupSecret {
    /** The 12 BIP39 words, as typed (import on a fresh install or in Settings). */
    class RecoveryPhrase(val words: CharArray) : BackupSecret()

    /** The vault's stored `backupRoot` (export from an unlocked vault). See [BackupKeys]. */
    class RecoveryRoot(val root: ByteArray) : BackupSecret()
}

/**
 * Seals a plaintext payload into the [BackupFormat] container and opens it again.
 * Derived keys are zeroized before returning, on success and on failure.
 */
object BackupCipher {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val random = SecureRandom()

    fun seal(plaintext: ByteArray, secret: BackupSecret): ByteArray {
        val salt = ByteArray(BackupFormat.SALT_LENGTH).also(random::nextBytes)
        val nonce = ByteArray(BackupFormat.NONCE_LENGTH).also(random::nextBytes)
        val header = BackupFormat.encodeHeader(
            kdf = BackupKdf.RECOVERY_PHRASE_HKDF_SHA256,
            salt = salt,
            nonce = nonce,
            ciphertextLength = plaintext.size + BackupFormat.TAG_LENGTH,
        )
        val key = deriveKey(secret, salt)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), gcmSpec(nonce))
            cipher.updateAAD(header)
            header + cipher.doFinal(plaintext)
        } finally {
            SensitiveDataCleaner.clear(key)
        }
    }

    /** Parses the header only: rejects non-backups and unsupported versions before any typing. */
    fun inspect(file: ByteArray): BackupHeader = BackupFormat.parse(file).header

    /**
     * Authenticates and decrypts [file]. Returns the plaintext payload, which the caller owns
     * and must wipe. Throws [BackupException] on any structural or authentication failure.
     */
    fun open(file: ByteArray, secret: BackupSecret): ByteArray {
        val parsed = BackupFormat.parse(file)
        val key = deriveKey(secret, parsed.header.salt)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), gcmSpec(parsed.header.nonce))
            // Offsets instead of copies: a large backup is held in memory once, not twice.
            cipher.updateAAD(parsed.file, 0, BackupFormat.HEADER_LENGTH)
            cipher.doFinal(parsed.file, parsed.ciphertextOffset, parsed.header.ciphertextLength)
        } catch (e: GeneralSecurityException) {
            // AEADBadTagException: wrong recovery phrase, or a modified header/ciphertext.
            throw BackupException(BackupError.WRONG_SECRET, e)
        } finally {
            SensitiveDataCleaner.clear(key)
        }
    }

    private fun gcmSpec(nonce: ByteArray) = GCMParameterSpec(BackupFormat.TAG_LENGTH * Byte.SIZE_BITS, nonce)

    private fun deriveKey(secret: BackupSecret, salt: ByteArray): ByteArray = when (secret) {
        is BackupSecret.RecoveryRoot -> BackupKeys.fileKeyFromRoot(secret.root, salt)

        is BackupSecret.RecoveryPhrase -> {
            val root = BackupKeys.rootFromMnemonic(secret.words)
            try {
                BackupKeys.fileKeyFromRoot(root, salt)
            } finally {
                SensitiveDataCleaner.clear(root)
            }
        }
    }
}
