package com.pasich.encly.core.security

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

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
    const val GCM_TAG_LENGTH = 128
    const val GCM_TAG_BYTES = GCM_TAG_LENGTH / 8
    const val IV_LENGTH = 12
    private const val SLOT_LENGTH = 1 + PIN_SALT_SIZE + IV_LENGTH + DEK_LENGTH + GCM_TAG_BYTES
    private const val WIPE_MARKER_LENGTH = 32
    private const val WIPE_SLOT_LENGTH = 1 + IV_LENGTH + WIPE_MARKER_LENGTH + GCM_TAG_BYTES
    const val AES_GCM = "AES/GCM/NoPadding"

    private val PIN_AAD = "encly/pin/slot/v3".toByteArray(Charsets.UTF_8)
    private val WIPE_AAD = "encly/pin/wipe-slot/v3".toByteArray(Charsets.UTF_8)

    /** `version ‖ salt ‖ iv ‖ AES-GCM(dek)`, with the version and salt authenticated as AAD. */
    fun sealSlot(dek: ByteArray, kek: ByteArray, salt: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"))
        cipher.updateAAD(aad(salt))
        val encrypted = cipher.doFinal(dek)
        val iv = cipher.iv
        return try {
            byteArrayOf(SLOT_VERSION) + salt + iv + encrypted
        } finally {
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

    /** The DEK in [slot], or null when [kek] does not open it. The caller wipes the result. */
    fun openSlotOrNull(slot: ParsedSlot, kek: ByteArray): ByteArray? = try {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, slot.iv))
        cipher.updateAAD(aad(slot.salt))
        cipher.doFinal(slot.ciphertext)
    } catch (_: GeneralSecurityException) {
        // AEADBadTagException included: a wrong PIN.
        null
    }

    fun aad(salt: ByteArray): ByteArray = PIN_AAD + byteArrayOf(SLOT_VERSION) + salt

    /** `version ‖ iv ‖ AES-GCM(random marker)`, bound to the PIN slot's [salt] as AAD. */
    fun sealWipeSlot(wipeKek: ByteArray, salt: ByteArray): ByteArray {
        val marker = ByteArray(WIPE_MARKER_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(wipeKek, "AES"))
        cipher.updateAAD(wipeAad(salt))
        val encrypted = try {
            cipher.doFinal(marker)
        } finally {
            SensitiveDataCleaner.clear(marker)
        }
        return try {
            byteArrayOf(SLOT_VERSION) + cipher.iv + encrypted
        } finally {
            SensitiveDataCleaner.clear(encrypted)
        }
    }

    /** Whether [wipeKek] authenticates [wipeSlot]; a decoy never does. */
    fun opensWipeSlot(wipeSlot: ByteArray, wipeKek: ByteArray, salt: ByteArray): Boolean {
        if (wipeSlot.size != WIPE_SLOT_LENGTH || wipeSlot[0] != SLOT_VERSION) return false
        val iv = wipeSlot.copyOfRange(1, 1 + IV_LENGTH)
        val ciphertext = wipeSlot.copyOfRange(1 + IV_LENGTH, wipeSlot.size)
        return try {
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(wipeKek, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            cipher.updateAAD(wipeAad(salt))
            SensitiveDataCleaner.clear(cipher.doFinal(ciphertext))
            true
        } catch (_: GeneralSecurityException) {
            false
        } finally {
            SensitiveDataCleaner.clear(iv)
            SensitiveDataCleaner.clear(ciphertext)
        }
    }

    fun wipeAad(salt: ByteArray): ByteArray = WIPE_AAD + byteArrayOf(SLOT_VERSION) + salt

    /** Random bytes shaped like a wipe slot (the same version byte and length): no PIN opens it. */
    fun decoyWipeSlot(): ByteArray = ByteArray(WIPE_SLOT_LENGTH).also {
        SecureRandom().nextBytes(it)
        it[0] = SLOT_VERSION
    }

    class ParsedSlot(val salt: ByteArray, val iv: ByteArray, val ciphertext: ByteArray) {
        fun wipe() {
            SensitiveDataCleaner.clear(salt)
            SensitiveDataCleaner.clear(iv)
            SensitiveDataCleaner.clear(ciphertext)
        }
    }
}
