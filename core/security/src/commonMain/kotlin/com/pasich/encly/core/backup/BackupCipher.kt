package com.pasich.encly.core.backup

import com.pasich.encly.core.crypto.Crypto
import com.pasich.encly.core.security.SensitiveDataCleaner

/**
 * What the per-file key is derived from. Both forms describe the same recovery phrase; the
 * caller keeps ownership of the wrapped arrays and wipes them. [BackupCipher] never retains them.
 */
public sealed class BackupSecret {
    /** The 12 BIP39 words, as typed (import on a fresh install or in Settings). */
    public class RecoveryPhrase(public val words: CharArray) : BackupSecret()

    /** The vault's stored `backupRoot` (export from an unlocked vault). See [BackupKeys]. */
    public class RecoveryRoot(public val root: ByteArray) : BackupSecret()
}

/**
 * Seals a plaintext payload into the [BackupFormat] container and opens it again.
 * Derived keys are zeroized before returning, on success and on failure.
 */
public object BackupCipher {
    public fun seal(plaintext: ByteArray, secret: BackupSecret): ByteArray {
        val salt = Crypto.randomBytes(BackupFormat.SALT_LENGTH)
        val nonce = Crypto.randomBytes(BackupFormat.NONCE_LENGTH)
        val header = BackupFormat.encodeHeader(
            kdf = BackupKdf.RECOVERY_PHRASE_HKDF_SHA256,
            salt = salt,
            nonce = nonce,
            ciphertextLength = plaintext.size + BackupFormat.TAG_LENGTH,
        )
        val key = deriveKey(secret, salt)
        return try {
            header + Crypto.aesGcmSeal(key, nonce, header, plaintext)
        } finally {
            SensitiveDataCleaner.clear(key)
        }
    }

    /** Parses the header only: rejects non-backups and unsupported versions before any typing. */
    public fun inspect(file: ByteArray): BackupHeader = BackupFormat.parse(file).header

    /**
     * Authenticates and decrypts [file]. Returns the plaintext payload, which the caller owns
     * and must wipe. Throws [BackupException] on any structural or authentication failure.
     */
    public fun open(file: ByteArray, secret: BackupSecret): ByteArray {
        val parsed = BackupFormat.parse(file)
        val key = deriveKey(secret, parsed.header.salt)
        return try {
            // The library takes whole arrays, so the header and the ciphertext are copied out of the
            // file (neither is secret): a large backup's ciphertext is held in memory twice.
            val header = parsed.file.copyOf(BackupFormat.HEADER_LENGTH)
            val ciphertext = parsed.file.copyOfRange(
                parsed.ciphertextOffset,
                parsed.ciphertextOffset + parsed.header.ciphertextLength,
            )
            // Null: wrong recovery phrase, or a modified header/ciphertext.
            Crypto.aesGcmOpen(key, parsed.header.nonce, header, ciphertext)
                ?: throw BackupException(BackupError.WRONG_SECRET)
        } finally {
            SensitiveDataCleaner.clear(key)
        }
    }

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
