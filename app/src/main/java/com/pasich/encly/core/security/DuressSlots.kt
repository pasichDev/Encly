package com.pasich.encly.core.security

import com.pasich.encly.core.security.PinSlots.AES_GCM
import com.pasich.encly.core.security.PinSlots.DEK_LENGTH
import com.pasich.encly.core.security.PinSlots.GCM_TAG_BYTES
import com.pasich.encly.core.security.PinSlots.GCM_TAG_LENGTH
import com.pasich.encly.core.security.PinSlots.IV_LENGTH
import com.pasich.encly.core.security.PinSlots.SLOT_VERSION
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The duress slot (`version ‖ iv ‖ AES-GCM_dkek(decoy DEK)`, bound to the PIN slot's salt) and
 * the decoy key (the same DEK sealed under a sub-key of the real vault's DEK): both 61 bytes,
 * the same as random filler, so the store has the same shape with or without a duress PIN.
 */
internal object DuressSlots {
    private const val DECOY_SLOT_LENGTH = 1 + IV_LENGTH + DEK_LENGTH + GCM_TAG_BYTES
    private val DECOY_AAD = "encly/pin/decoy-slot/v3".toByteArray(Charsets.UTF_8)
    val DECOY_KEY_AAD = "encly/decoy/key/v1".toByteArray(Charsets.UTF_8) + byteArrayOf(SLOT_VERSION)

    /** The decoy key while its duress PIN is turned off (a new PIN slot killed the duress slot). */
    val DECOY_KEY_OFF_AAD = "encly/decoy/key/off/v1".toByteArray(Charsets.UTF_8) + byteArrayOf(SLOT_VERSION)
    private val DECOY_KEY_SALT = "encly/decoy/key/salt/v1".toByteArray(Charsets.UTF_8)
    private val DECOY_KEY_INFO = "encly/decoy/key/kek/v1".toByteArray(Charsets.UTF_8)

    /** A sub-key of the vault's DEK, so the SQLCipher key itself is never an AES-GCM key. */
    fun decoyKeyKek(primaryDek: ByteArray): ByteArray =
        Hkdf.sha256(ikm = primaryDek, salt = DECOY_KEY_SALT, info = DECOY_KEY_INFO, length = DEK_LENGTH)

    /** `version ‖ iv ‖ AES-GCM(dek)`: the shape of the duress slot and the decoy key, 61 bytes. */
    fun sealDecoy(dek: ByteArray, kek: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"))
        cipher.updateAAD(aad)
        val encrypted = cipher.doFinal(dek)
        return try {
            byteArrayOf(SLOT_VERSION) + cipher.iv + encrypted
        } finally {
            SensitiveDataCleaner.clear(encrypted)
        }
    }

    fun openDecoy(sealed: ByteArray, kek: ByteArray, aad: ByteArray): ByteArray? {
        if (sealed.size != DECOY_SLOT_LENGTH || sealed[0] != SLOT_VERSION) return null
        val iv = sealed.copyOfRange(1, 1 + IV_LENGTH)
        val ciphertext = sealed.copyOfRange(1 + IV_LENGTH, sealed.size)
        return try {
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            cipher.updateAAD(aad)
            cipher.doFinal(ciphertext)
        } catch (_: GeneralSecurityException) {
            null
        } finally {
            SensitiveDataCleaner.clear(iv)
            SensitiveDataCleaner.clear(ciphertext)
        }
    }

    fun decoySlotAad(salt: ByteArray): ByteArray = DECOY_AAD + byteArrayOf(SLOT_VERSION) + salt

    /** Random bytes shaped like a duress slot or decoy key: nothing opens them. */
    fun randomSlot(): ByteArray = ByteArray(DECOY_SLOT_LENGTH).also {
        SecureRandom().nextBytes(it)
        it[0] = SLOT_VERSION
    }
}
