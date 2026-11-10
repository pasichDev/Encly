package com.pasich.encly.core.security

import com.pasich.encly.core.crypto.Crypto

/**
 * The byte formats of the PIN-derived slots in the vault store, and their AES-256-GCM sealing:
 * the PIN slot (`version ‖ salt ‖ iv ‖ AES-GCM(DEK)`), the wipe slot and the duress slot (both
 * `version ‖ iv ‖ AES-GCM(…)`, bound to the PIN slot's salt; see [DuressSlots] for the duress
 * slot and the decoy key). Pure functions:
 * which slot is read or written, and when, is [AuthenticationManager]'s business.
 */
internal object PinSlots {
    const val PIN_SALT_SIZE = 16
    const val DEK_LENGTH = 32
    const val SLOT_VERSION: Byte = 3
    const val GCM_TAG_BYTES = Crypto.TAG_BYTES
    const val IV_LENGTH = Crypto.NONCE_BYTES
    private const val SLOT_LENGTH = 1 + PIN_SALT_SIZE + IV_LENGTH + DEK_LENGTH + GCM_TAG_BYTES
    private const val WIPE_MARKER_LENGTH = 32
    private const val WIPE_SLOT_LENGTH = 1 + IV_LENGTH + WIPE_MARKER_LENGTH + GCM_TAG_BYTES

    private val PIN_AAD = "encly/pin/slot/v3".encodeToByteArray()
    private val WIPE_AAD = "encly/pin/wipe-slot/v3".encodeToByteArray()

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
        val salt = slot.copyOfRange(offset, offset + PIN_SALT_SIZE)
        offset += PIN_SALT_SIZE
        val iv = slot.copyOfRange(offset, offset + IV_LENGTH)
        offset += IV_LENGTH
        return ParsedSlot(salt, iv, slot.copyOfRange(offset, slot.size))
    }

    /** The DEK in [slot], or null when [kek] does not open it (a wrong PIN). The caller wipes the result. */
    fun openSlotOrNull(slot: ParsedSlot, kek: ByteArray): ByteArray? =
        Crypto.aesGcmOpen(kek, slot.iv, aad(slot.salt), slot.ciphertext)

    fun aad(salt: ByteArray): ByteArray = PIN_AAD + byteArrayOf(SLOT_VERSION) + salt

    /** `version ‖ iv ‖ AES-GCM(random marker)`, bound to the PIN slot's [salt] as AAD. */
    fun sealWipeSlot(wipeKek: ByteArray, salt: ByteArray): ByteArray {
        val marker = Crypto.randomBytes(WIPE_MARKER_LENGTH)
        val iv = Crypto.randomBytes(IV_LENGTH)
        val encrypted = try {
            Crypto.aesGcmSeal(wipeKek, iv, wipeAad(salt), marker)
        } finally {
            SensitiveDataCleaner.clear(marker)
        }
        return try {
            byteArrayOf(SLOT_VERSION) + iv + encrypted
        } finally {
            SensitiveDataCleaner.clear(iv)
            SensitiveDataCleaner.clear(encrypted)
        }
    }

    /** Whether [wipeKek] authenticates [wipeSlot]; a decoy never does. */
    fun opensWipeSlot(wipeSlot: ByteArray, wipeKek: ByteArray, salt: ByteArray): Boolean {
        if (wipeSlot.size != WIPE_SLOT_LENGTH || wipeSlot[0] != SLOT_VERSION) return false
        val iv = wipeSlot.copyOfRange(1, 1 + IV_LENGTH)
        val ciphertext = wipeSlot.copyOfRange(1 + IV_LENGTH, wipeSlot.size)
        return try {
            val marker = Crypto.aesGcmOpen(wipeKek, iv, wipeAad(salt), ciphertext)
            if (marker != null) SensitiveDataCleaner.clear(marker)
            marker != null
        } finally {
            SensitiveDataCleaner.clear(iv)
            SensitiveDataCleaner.clear(ciphertext)
        }
    }

    fun wipeAad(salt: ByteArray): ByteArray = WIPE_AAD + byteArrayOf(SLOT_VERSION) + salt

    /** Random bytes shaped like a wipe slot (the same version byte and length): no PIN opens it. */
    fun decoyWipeSlot(): ByteArray = Crypto.randomBytes(WIPE_SLOT_LENGTH).also { it[0] = SLOT_VERSION }

    class ParsedSlot(val salt: ByteArray, val iv: ByteArray, val ciphertext: ByteArray) {
        fun wipe() {
            SensitiveDataCleaner.clear(salt)
            SensitiveDataCleaner.clear(iv)
            SensitiveDataCleaner.clear(ciphertext)
        }
    }
}
