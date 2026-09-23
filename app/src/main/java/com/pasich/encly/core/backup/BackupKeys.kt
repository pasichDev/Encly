package com.pasich.encly.core.backup

import com.pasich.encly.core.security.Hkdf
import com.pasich.encly.core.security.SensitiveDataCleaner
import java.nio.CharBuffer
import java.security.MessageDigest

/**
 * Key derivation for encrypted backups. Every function returns a fresh array the caller owns
 * and must wipe; inputs are never retained.
 *
 * Recovery-phrase backups use a two-level HKDF-SHA256 chain:
 *
 *     seedHash = SHA-256(normalized BIP39 words)
 *     backupRoot = HKDF(ikm = seedHash, salt = ROOT_SALT, info = ROOT_INFO)
 *     fileKey = HKDF(ikm = backupRoot, salt = <32 random bytes per file>, info = FILE_INFO)
 *
 * The info strings differ from the vault recovery KEK ("encly/recovery/kek/v2"), so a backup
 * key can never unwrap the vault and vice versa. The vault keeps only `backupRoot` (wrapped
 * under a DEK sub-key) so an unlocked session can export without asking for the 12 words;
 * a fresh install re-derives it from the words.
 *
 * The phrase carries 128 bits of entropy, so a fast KDF is sufficient; there is deliberately
 * no user-chosen backup passphrase (see SECURITY.md).
 */
internal object BackupKeys {
    const val KEY_LENGTH = 32

    private val ROOT_SALT = "encly/backup/salt/v1".toByteArray(Charsets.UTF_8)
    private val ROOT_INFO = "encly/backup/root/v1".toByteArray(Charsets.UTF_8)
    private val FILE_INFO = "encly/backup/file/v1".toByteArray(Charsets.UTF_8)

    /** Lower-cases and collapses any whitespace between words to a single space. */
    fun normalizeMnemonic(words: CharArray): CharArray {
        val out = CharArray(words.size)
        var length = 0
        var pendingSpace = false
        for (c in words) {
            if (c.isWhitespace()) {
                pendingSpace = length > 0
            } else {
                if (pendingSpace) {
                    out[length++] = ' '
                    pendingSpace = false
                }
                out[length++] = c.lowercaseChar()
            }
        }
        return try {
            out.copyOf(length)
        } finally {
            SensitiveDataCleaner.clear(out)
        }
    }

    fun rootFromMnemonic(words: CharArray): ByteArray {
        val normalized = normalizeMnemonic(words)
        val utf8 = utf8(normalized)
        SensitiveDataCleaner.clear(normalized)
        val seedHash = MessageDigest.getInstance("SHA-256").digest(utf8)
        SensitiveDataCleaner.clear(utf8)
        return try {
            Hkdf.sha256(seedHash, ROOT_SALT, ROOT_INFO, KEY_LENGTH)
        } finally {
            SensitiveDataCleaner.clear(seedHash)
        }
    }

    fun fileKeyFromRoot(root: ByteArray, salt: ByteArray): ByteArray {
        require(root.size == KEY_LENGTH)
        return Hkdf.sha256(root, salt, FILE_INFO, KEY_LENGTH)
    }

    private fun utf8(chars: CharArray): ByteArray {
        val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(chars))
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        if (buffer.hasArray()) SensitiveDataCleaner.clear(buffer.array())
        return bytes
    }
}
