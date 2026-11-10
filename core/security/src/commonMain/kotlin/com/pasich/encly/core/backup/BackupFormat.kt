package com.pasich.encly.core.backup

import kotlinx.io.Buffer
import kotlinx.io.readByteArray

/** Why a backup could not be written or read. Never carries secrets or plaintext. */
public enum class BackupError {
    /** Not an Encly backup (wrong magic). */
    NOT_A_BACKUP,

    /** An Encly backup written by a newer (or unknown) format version. */
    UNSUPPORTED_VERSION,

    /** The file ends before the header or ciphertext does. */
    TRUNCATED,

    /** Malformed header fields or trailing bytes after the ciphertext. */
    CORRUPTED,

    /**
     * Larger than [BackupFormat.MAX_FILE_BYTES]: on import the file, on export the vault, which
     * is refused rather than written as a file no install could read back.
     */
    TOO_LARGE,

    /** Export target already holds data; it is never overwritten (see BackupDocuments.write). */
    TARGET_EXISTS,

    /** AES-GCM authentication failed: wrong recovery phrase, or a modified header/ciphertext. */
    WRONG_SECRET,

    /** Authenticated, but the payload is not a valid Encly backup payload. */
    INVALID_PAYLOAD,

    /** The words are not a valid 12-word BIP39 recovery phrase. */
    INVALID_PHRASE,

    /** The vault could not provide the backup key (e.g. it was locked meanwhile). */
    VAULT_UNAVAILABLE,

    /** Reading, writing or importing failed for a reason other than the above. */
    IO,
}

public class BackupException(public val error: BackupError, cause: Throwable? = null) : Exception(error.name, cause)

/**
 * How the per-file key is derived; stored in (and authenticated with) the header so a future
 * format can add another derivation without guessing.
 */
public enum class BackupKdf(public val id: Int) {
    /** HKDF-SHA256 from the BIP39 recovery phrase. See [BackupKeys]. */
    RECOVERY_PHRASE_HKDF_SHA256(1),
    ;

    public companion object {
        public fun fromId(id: Int): BackupKdf? = entries.firstOrNull { it.id == id }
    }
}

public class BackupHeader(
    public val formatVersion: Int,
    public val kdf: BackupKdf,
    public val salt: ByteArray,
    public val nonce: ByteArray,
    public val ciphertextLength: Int,
)

/**
 * A parsed file: the header plus the whole [file], which it does not copy. The first
 * [BackupFormat.HEADER_LENGTH] bytes are the GCM AAD; the ciphertext with tag follows them.
 */
public class ParsedBackup(public val header: BackupHeader, public val file: ByteArray) {
    public val ciphertextOffset: Int get() = BackupFormat.HEADER_LENGTH
}

/**
 * Encly backup container, format version 1. All integers are unsigned big-endian.
 *
 * | offset | size | field                                                  |
 * |-------:|-----:|--------------------------------------------------------|
 * |      0 |    8 | magic `ENCLYBAK` (ASCII)                               |
 * |      8 |    2 | format version (= 1)                                   |
 * |     10 |    1 | KDF id (1 = HKDF-SHA256 from the recovery phrase)      |
 * |     11 |    1 | salt length (= 32)                                     |
 * |     12 |   32 | HKDF salt (random per file)                            |
 * |     44 |    1 | nonce length (= 12)                                    |
 * |     45 |   12 | AES-256-GCM nonce (random per file)                    |
 * |     57 |    4 | ciphertext length N, including the 16-byte GCM tag     |
 * |     61 |    N | AES-256-GCM ciphertext ‖ tag of the UTF-8 JSON payload |
 *
 * The 61 header bytes are the GCM additional authenticated data, so changing any header
 * field (version, KDF, salt, nonce, length) fails authentication. Nothing may
 * follow the ciphertext.
 */
public object BackupFormat {
    public val MAGIC: ByteArray = "ENCLYBAK".encodeToByteArray()
    public const val VERSION: Int = 1
    public const val SALT_LENGTH: Int = 32
    public const val NONCE_LENGTH: Int = 12
    public const val TAG_LENGTH: Int = 16
    public const val HEADER_LENGTH: Int = 61
    public const val MAX_FILE_BYTES: Int = 64 * 1024 * 1024
    public const val FILE_EXTENSION: String = "enclybak"
    public const val MIME_TYPE: String = "application/octet-stream"

    private const val VERSION_END = 10
    private const val U8_MASK = 0xFF
    private const val U16_MASK = 0xFFFF
    private const val U32_MASK = 0xFFFF_FFFFL

    public fun encodeHeader(kdf: BackupKdf, salt: ByteArray, nonce: ByteArray, ciphertextLength: Int): ByteArray {
        require(salt.size == SALT_LENGTH && nonce.size == NONCE_LENGTH)
        val header = Buffer()
        header.write(MAGIC)
        header.writeShort(VERSION.toShort())
        header.writeByte(kdf.id.toByte())
        header.writeByte(SALT_LENGTH.toByte())
        header.write(salt)
        header.writeByte(NONCE_LENGTH.toByte())
        header.write(nonce)
        header.writeInt(ciphertextLength)
        return header.readByteArray()
    }

    /**
     * The size of a file sealing [plaintextBytes] of payload. Export refuses anything above
     * [MAX_FILE_BYTES], so every file this app writes can be read back by [parse].
     */
    public fun fileSizeFor(plaintextBytes: Int): Long = HEADER_LENGTH.toLong() + plaintextBytes + TAG_LENGTH

    /** Validates the container structure. Does not touch keys or decrypt anything. */
    public fun parse(file: ByteArray): ParsedBackup {
        ensure(file.size <= MAX_FILE_BYTES, BackupError.TOO_LARGE)
        // The whole file is here: a length field that overruns it is a truncation, not a size.
        val header = parseHeader(file, limit = Long.MAX_VALUE)
        val available = file.size - HEADER_LENGTH
        ensure(available >= header.ciphertextLength, BackupError.TRUNCATED)
        ensure(available == header.ciphertextLength, BackupError.CORRUPTED)
        return ParsedBackup(header, file)
    }

    /**
     * Validates the header at the start of [bytes], which may hold just the header, and
     * rejects one whose declared file size exceeds [limit]. Lets a reader size its buffer from
     * the header instead of growing one.
     */
    public fun parseHeader(bytes: ByteArray, limit: Long = MAX_FILE_BYTES.toLong()): BackupHeader {
        ensure(
            bytes.size >= MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC),
            BackupError.NOT_A_BACKUP,
        )
        ensure(bytes.size >= VERSION_END, BackupError.TRUNCATED)
        // Only the header is copied: [bytes] may be the whole file.
        val buffer = Buffer()
        buffer.write(bytes, 0, minOf(bytes.size, HEADER_LENGTH))
        buffer.skip(MAGIC.size.toLong())
        val version = buffer.readShort().toInt() and U16_MASK
        ensure(version == VERSION, BackupError.UNSUPPORTED_VERSION)
        ensure(bytes.size >= HEADER_LENGTH, BackupError.TRUNCATED)

        val kdf = BackupKdf.fromId(buffer.readByte().toInt() and U8_MASK)
        ensure(kdf != null && (buffer.readByte().toInt() and U8_MASK) == SALT_LENGTH, BackupError.CORRUPTED)
        val salt = buffer.readByteArray(SALT_LENGTH)
        ensure((buffer.readByte().toInt() and U8_MASK) == NONCE_LENGTH, BackupError.CORRUPTED)
        val nonce = buffer.readByteArray(NONCE_LENGTH)
        // Unsigned: a length above Int.MAX_VALUE reads as negative and is simply too large.
        val ciphertextLength = buffer.readInt().toLong() and U32_MASK
        ensure(HEADER_LENGTH + ciphertextLength <= limit, BackupError.TOO_LARGE)
        ensure(ciphertextLength >= TAG_LENGTH, BackupError.CORRUPTED)
        return BackupHeader(version, checkNotNull(kdf), salt, nonce, ciphertextLength.toInt())
    }

    private fun ensure(condition: Boolean, error: BackupError) {
        if (!condition) throw BackupException(error)
    }
}
