package com.pasich.encly.core.security

import com.pasich.encly.core.crypto.Crypto
import com.pasich.encly.core.security.PinSlots.DEK_LENGTH
import com.pasich.encly.core.security.PinSlots.GCM_TAG_BYTES
import com.pasich.encly.core.security.PinSlots.IV_LENGTH
import com.pasich.encly.core.security.PinSlots.ParsedSlot

/**
 * The byte format of the password slot (`pin.password.slot`): `version ‖ salt ‖ iv ‖ AES-GCM(DEK)`,
 * the same shape and size as the PIN slot, with a version, an AAD and a KDF domain of its own so
 * that no key or ciphertext of the PIN is ever valid here. The KDF chain is the PIN's (PBKDF2 600 000,
 * the device-bound HMAC, HKDF); see [AuthenticationManager]. Pure functions.
 *
 * An older build never reads this entry: it does not know the key, so it ignores it (and drops it
 * with every other `pin.` entry when it wipes the PIN state).
 */
internal object PasswordSlots {
    const val SALT_SIZE = 16
    const val SLOT_VERSION: Byte = 1
    private const val SLOT_LENGTH = 1 + SALT_SIZE + IV_LENGTH + DEK_LENGTH + GCM_TAG_BYTES

    private val SLOT_AAD = "encly/pw/slot/v1".encodeToByteArray()

    /** HKDF info of the password KEK. */
    val KEK_INFO = "encly/pw/kek/v1".encodeToByteArray()

    /** Prefix of the device-bound HMAC input (`tag ‖ salt ‖ UTF-8 password`): the PIN's input has none. */
    val HARDWARE_TAG = "encly/pw/hw/v1".encodeToByteArray()

    /** `version ‖ salt ‖ iv ‖ AES-GCM(dek)`, with the version and salt authenticated as AAD. */
    fun sealSlot(dek: ByteArray, kek: ByteArray, salt: ByteArray): ByteArray {
        val iv = Crypto.randomBytes(IV_LENGTH)
        val encrypted = Crypto.aesGcmSeal(kek, iv, aad(salt), dek)
        return try {
            byteArrayOf(SLOT_VERSION) + salt + iv + encrypted
        } finally {
            SensitiveDataCleaner.clear(iv)
            SensitiveDataCleaner.clear(encrypted)
        }
    }

    fun parseSlot(slot: ByteArray): ParsedSlot? {
        if (slot.size != SLOT_LENGTH || slot[0] != SLOT_VERSION) return null
        var offset = 1
        val salt = slot.copyOfRange(offset, offset + SALT_SIZE)
        offset += SALT_SIZE
        val iv = slot.copyOfRange(offset, offset + IV_LENGTH)
        offset += IV_LENGTH
        return ParsedSlot(salt, iv, slot.copyOfRange(offset, slot.size))
    }

    /** The DEK in [slot], or null when [kek] does not open it (a wrong password). The caller wipes the result. */
    fun openSlotOrNull(slot: ParsedSlot, kek: ByteArray): ByteArray? =
        Crypto.aesGcmOpen(kek, slot.iv, aad(slot.salt), slot.ciphertext)

    fun aad(salt: ByteArray): ByteArray = SLOT_AAD + byteArrayOf(SLOT_VERSION) + salt
}

/**
 * The rules for a password: Unicode, NFC-normalised (so the same word typed on another keyboard or
 * platform is the same bytes), at least [MIN_LENGTH] and at most [MAX_LENGTH] characters (code points).
 */
internal object Passwords {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 128

    /** Every character below this is its own NFC form and cannot combine with the one before it. */
    private const val FIRST_COMBINING = 0x300

    /**
     * [password] in NFC, in a fresh array; the caller wipes both. Plain Latin text needs no
     * normalising and no String is made; other text is normalised by the platform, which cannot
     * promise to wipe what it made on the way.
     */
    fun normalize(password: CharArray): CharArray =
        if (password.all { it.code < FIRST_COMBINING }) password.copyOf() else normalizeNfc(password)

    /** Whether [normalized] has an acceptable number of characters to be set as the password. */
    fun isAcceptable(normalized: CharArray): Boolean = codePoints(normalized) in MIN_LENGTH..MAX_LENGTH

    /** Whether [normalized] is worth a key derivation when unlocking: not empty, not absurdly long. */
    fun isPlausible(normalized: CharArray): Boolean = normalized.isNotEmpty() && normalized.size <= MAX_LENGTH * 2

    /** Characters as Unicode code points: a surrogate pair is one. */
    fun codePoints(chars: CharArray): Int = chars.count { !it.isLowSurrogate() }
}

/** [text] in Unicode normalization form C. */
internal expect fun normalizeNfc(text: CharArray): CharArray
