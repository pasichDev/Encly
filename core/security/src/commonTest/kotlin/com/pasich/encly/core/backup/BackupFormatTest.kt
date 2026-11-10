package com.pasich.encly.core.backup

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** The container header byte by byte, and the order in which a damaged one is reported. */
class BackupFormatTest {

    private val salt = ByteArray(BackupFormat.SALT_LENGTH) { (it + 1).toByte() }
    private val nonce = ByteArray(BackupFormat.NONCE_LENGTH) { (0x80 + it).toByte() }

    private fun header(ciphertextLength: Int = 100) =
        BackupFormat.encodeHeader(BackupKdf.RECOVERY_PHRASE_HKDF_SHA256, salt, nonce, ciphertextLength)

    /** A header with [ciphertextLength] declared, followed by that many ciphertext bytes. */
    private fun file(ciphertextLength: Int = 100) = header(ciphertextLength) + ByteArray(ciphertextLength)

    private fun ByteArray.with(at: Int, value: Int) = copyOf().also { it[at] = value.toByte() }

    @Test
    fun theHeaderIsBigEndianAtTheDocumentedOffsets() {
        val bytes = header(ciphertextLength = 0x01020304)

        assertEquals(BackupFormat.HEADER_LENGTH, bytes.size)
        assertContentEquals("ENCLYBAK".encodeToByteArray(), bytes.copyOfRange(0, 8))
        assertContentEquals(byteArrayOf(0, 1), bytes.copyOfRange(8, 10))
        assertEquals(1, bytes[10].toInt())
        assertEquals(BackupFormat.SALT_LENGTH, bytes[11].toInt())
        assertContentEquals(salt, bytes.copyOfRange(12, 44))
        assertEquals(BackupFormat.NONCE_LENGTH, bytes[44].toInt())
        assertContentEquals(nonce, bytes.copyOfRange(45, 57))
        assertContentEquals(byteArrayOf(1, 2, 3, 4), bytes.copyOfRange(57, 61))
    }

    @Test
    fun aHeaderParsesBackIntoItsFields() {
        val parsed = BackupFormat.parse(file(ciphertextLength = 100))

        assertEquals(BackupFormat.VERSION, parsed.header.formatVersion)
        assertEquals(BackupKdf.RECOVERY_PHRASE_HKDF_SHA256, parsed.header.kdf)
        assertContentEquals(salt, parsed.header.salt)
        assertContentEquals(nonce, parsed.header.nonce)
        assertEquals(100, parsed.header.ciphertextLength)
    }

    @Test
    fun theMagicIsCheckedFirstThenTheVersionThenTheLengthOfTheHeader() {
        assertError(BackupError.NOT_A_BACKUP) { BackupFormat.parseHeader(ByteArray(0)) }
        assertError(BackupError.NOT_A_BACKUP) { BackupFormat.parseHeader(header().with(0, 'X'.code)) }
        // Magic only: the version is not there yet.
        assertError(BackupError.TRUNCATED) { BackupFormat.parseHeader(header().copyOf(9)) }
        // A newer version is reported even when the rest of the header is missing or wrong.
        assertError(BackupError.UNSUPPORTED_VERSION) {
            BackupFormat.parseHeader(header().with(9, 2).copyOf(BackupFormat.HEADER_LENGTH - 1))
        }
        assertError(BackupError.TRUNCATED) { BackupFormat.parseHeader(header().copyOf(BackupFormat.HEADER_LENGTH - 1)) }
    }

    @Test
    fun anUnknownKdfOrAWrongSaltOrNonceLengthIsCorrupted() {
        assertError(BackupError.CORRUPTED) { BackupFormat.parseHeader(header().with(10, 0x7f)) }
        assertError(BackupError.CORRUPTED) { BackupFormat.parseHeader(header().with(11, 16)) }
        assertError(BackupError.CORRUPTED) { BackupFormat.parseHeader(header().with(44, 8)) }
    }

    @Test
    fun theDeclaredSizeIsCheckedAgainstTheLimitBeforeItIsTrustedAsATagCarrier() {
        val limit = (BackupFormat.HEADER_LENGTH + 100).toLong()
        assertEquals(100, BackupFormat.parseHeader(header(100), limit).ciphertextLength)
        assertError(BackupError.TOO_LARGE) { BackupFormat.parseHeader(header(101), limit) }
        // 0xFFFFFFFF is a huge unsigned length, not -1.
        assertError(BackupError.TOO_LARGE) { BackupFormat.parseHeader(header(-1)) }
        // Smaller than the GCM tag it must contain.
        assertError(BackupError.CORRUPTED) { BackupFormat.parseHeader(header(BackupFormat.TAG_LENGTH - 1)) }
        assertEquals(
            BackupFormat.TAG_LENGTH,
            BackupFormat.parseHeader(header(BackupFormat.TAG_LENGTH)).ciphertextLength,
        )
    }

    @Test
    fun theWholeFileIsCheckedAgainstTheDeclaredLength() {
        val whole = file(100)
        assertError(BackupError.TRUNCATED) { BackupFormat.parse(whole.copyOf(whole.size - 1)) }
        assertError(BackupError.CORRUPTED) { BackupFormat.parse(whole + byteArrayOf(0)) }
        assertError(BackupError.TOO_LARGE) { BackupFormat.parse(ByteArray(BackupFormat.MAX_FILE_BYTES + 1)) }
        // A length field that overruns the file it sits in is a truncation, not a size problem.
        assertError(BackupError.TRUNCATED) { BackupFormat.parse(header(BackupFormat.MAX_FILE_BYTES) + ByteArray(20)) }
    }

    @Test
    fun parsingReadsOnlyTheHeaderOfALargeFile() {
        val whole = file(1_000_000)

        assertEquals(1_000_000, BackupFormat.parse(whole).header.ciphertextLength)
        assertEquals(BackupFormat.HEADER_LENGTH, BackupFormat.parse(whole).ciphertextOffset)
    }
}
