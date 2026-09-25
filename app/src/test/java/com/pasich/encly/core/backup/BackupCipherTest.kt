package com.pasich.encly.core.backup

import cash.z.ecc.android.bip39.Mnemonics.MnemonicCode
import cash.z.ecc.android.bip39.Mnemonics.WordCount
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer

class BackupCipherTest {

    private val words = MnemonicCode(WordCount.COUNT_12).chars
    private val plaintext = "{\"notes\":[\"secret note body\"]}".toByteArray()

    private fun phrase(chars: CharArray = words) = BackupSecret.RecoveryPhrase(chars.copyOf())

    @Test
    fun sealedFileOpensWithTheSameWords() {
        val file = BackupCipher.seal(plaintext, phrase())

        assertArrayEquals(plaintext, BackupCipher.open(file, phrase()))
    }

    @Test
    fun theVaultsStoredRootAndTheTypedWordsDeriveTheSameKey() {
        // Export uses the backupRoot kept in the vault; a fresh install only has the words.
        val root = BackupKeys.rootFromMnemonic(words.copyOf())
        val file = BackupCipher.seal(plaintext, BackupSecret.RecoveryRoot(root))

        assertArrayEquals(plaintext, BackupCipher.open(file, phrase()))
    }

    @Test
    fun wordsAreMatchedRegardlessOfCaseAndSpacing() {
        val file = BackupCipher.seal(plaintext, phrase())
        val sloppy = ("  " + String(words).uppercase().replace(" ", "   \n") + " ").toCharArray()

        assertArrayEquals(plaintext, BackupCipher.open(file, phrase(sloppy)))
    }

    @Test
    fun wrongWordsAreRejected() {
        val file = BackupCipher.seal(plaintext, phrase())
        val other = MnemonicCode(WordCount.COUNT_12).chars

        assertError(BackupError.WRONG_SECRET) { BackupCipher.open(file, phrase(other)) }
    }

    @Test
    fun everyFileHasItsOwnSaltAndNonce() {
        val first = BackupCipher.seal(plaintext, phrase())
        val second = BackupCipher.seal(plaintext, phrase())

        assertFalse(first.contentEquals(second))
        assertFalse(BackupCipher.inspect(first).salt.contentEquals(BackupCipher.inspect(second).salt))
        assertFalse(BackupCipher.inspect(first).nonce.contentEquals(BackupCipher.inspect(second).nonce))
    }

    @Test
    fun theFileNeverContainsThePlaintext() {
        val file = BackupCipher.seal(plaintext, phrase())

        assertFalse(String(file, Charsets.ISO_8859_1).contains("secret note body"))
    }

    @Test
    fun headerDescribesTheFormat() {
        val header = BackupCipher.inspect(BackupCipher.seal(plaintext, phrase()))

        assertEquals(BackupFormat.VERSION, header.formatVersion)
        assertEquals(BackupKdf.RECOVERY_PHRASE_HKDF_SHA256, header.kdf)
        assertEquals(plaintext.size + BackupFormat.TAG_LENGTH, header.ciphertextLength)
    }

    @Test
    fun anyModifiedHeaderByteFailsAuthentication() {
        val file = BackupCipher.seal(plaintext, phrase())
        // Salt and nonce bytes: structurally valid, so only the AAD/key check can catch them.
        listOf(SALT_OFFSET, SALT_OFFSET + 31, NONCE_OFFSET, NONCE_OFFSET + 11).forEach { offset ->
            val tampered = file.copyOf().also { it[offset] = (it[offset].toInt() xor 0x01).toByte() }
            assertError(BackupError.WRONG_SECRET) { BackupCipher.open(tampered, phrase()) }
        }
    }

    @Test
    fun modifiedCiphertextOrTagFailsAuthentication() {
        val file = BackupCipher.seal(plaintext, phrase())
        listOf(BackupFormat.HEADER_LENGTH, file.size - 1).forEach { offset ->
            val tampered = file.copyOf().also { it[offset] = (it[offset].toInt() xor 0x80).toByte() }
            assertError(BackupError.WRONG_SECRET) { BackupCipher.open(tampered, phrase()) }
        }
    }

    @Test
    fun unknownKdfIsCorrupted() {
        val file = BackupCipher.seal(plaintext, phrase())
        file[KDF_OFFSET] = 0x7F

        assertError(BackupError.CORRUPTED) { BackupCipher.open(file, phrase()) }
    }

    @Test
    fun newerFormatVersionIsReportedBeforeAnyDecryption() {
        val file = BackupCipher.seal(plaintext, phrase())
        ByteBuffer.wrap(file).putShort(VERSION_OFFSET, (BackupFormat.VERSION + 1).toShort())

        assertError(BackupError.UNSUPPORTED_VERSION) { BackupCipher.inspect(file) }
        assertError(BackupError.UNSUPPORTED_VERSION) { BackupCipher.open(file, phrase()) }
    }

    @Test
    fun truncatedFilesAreRejected() {
        val file = BackupCipher.seal(plaintext, phrase())
        listOf(9, BackupFormat.HEADER_LENGTH - 1, BackupFormat.HEADER_LENGTH, file.size - 1).forEach { size ->
            assertError(BackupError.TRUNCATED) { BackupCipher.open(file.copyOf(size), phrase()) }
        }
    }

    @Test
    fun trailingBytesAreRejected() {
        val file = BackupCipher.seal(plaintext, phrase()) + byteArrayOf(0)

        assertError(BackupError.CORRUPTED) { BackupCipher.inspect(file) }
    }

    @Test
    fun otherFilesAreNotBackups() {
        assertError(BackupError.NOT_A_BACKUP) { BackupCipher.inspect(ByteArray(0)) }
        assertError(BackupError.NOT_A_BACKUP) { BackupCipher.inspect("%PDF-1.7 not a backup".toByteArray()) }
    }

    @Test
    fun oversizedFilesAreRejectedUnparsed() {
        assertError(BackupError.TOO_LARGE) { BackupCipher.inspect(ByteArray(BackupFormat.MAX_FILE_BYTES + 1)) }
    }

    @Test
    fun emptyPayloadRoundTrips() {
        val file = BackupCipher.seal(ByteArray(0), phrase())

        assertArrayEquals(ByteArray(0), BackupCipher.open(file, phrase()))
    }

    private companion object {
        const val VERSION_OFFSET = 8
        const val KDF_OFFSET = 10
        const val SALT_OFFSET = 12
        const val NONCE_OFFSET = 45
    }
}

internal fun assertError(expected: BackupError, block: () -> Unit) {
    try {
        block()
        fail("expected BackupException($expected)")
    } catch (e: BackupException) {
        assertEquals(expected, e.error)
    }
}
