@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.PinKeySlot
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFErrorGetCode
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.Security.SecAccessControlCreateWithFlags
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecKeyCopyPublicKey
import platform.Security.SecKeyCreateDecryptedData
import platform.Security.SecKeyCreateEncryptedData
import platform.Security.SecKeyCreateRandomKey
import platform.Security.SecKeyRef
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAccessControlPrivateKeyUsage
import platform.Security.kSecAttrAccessControl
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrApplicationTag
import platform.Security.kSecAttrIsPermanent
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecAttrTokenID
import platform.Security.kSecAttrTokenIDSecureEnclave
import platform.Security.kSecClass
import platform.Security.kSecClassKey
import platform.Security.kSecKeyAlgorithmECIESEncryptionCofactorX963SHA256AESGCM
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecPrivateKeyAttrs
import platform.Security.kSecReturnRef

/**
 * Wraps the device secret of a [PinKeySlot] with a key that never leaves the Secure Enclave: an EC P-256 private
 * key created inside it (`kSecAttrTokenIDSecureEnclave`, usable only while the device is unlocked, never
 * synchronized or backed up), and ECIES (`eciesEncryptionCofactorX963SHA256AESGCM`) for the secret itself. The
 * public half encrypts without the hardware; only decrypting asks the Enclave. The wrapped blob is what
 * [KeychainSecretStore] keeps in the Keychain, so a copy of the Keychain alone no longer holds the secret.
 *
 * The iOS Simulator has no Secure Enclave and some devices (and a locked one) cannot use it: [wrap] then returns
 * null and the store keeps the secret in the Keychain only. Nothing here runs on the Simulator past that first
 * check, so the Enclave path itself is exercised only on a device.
 */
internal class SecureEnclaveWrapper(private val service: String) {
    /**
     * [secret] encrypted to a new Enclave key for [slot] (replacing the key it had), or null when the Secure
     * Enclave cannot be used. The blob is not secret.
     */
    fun wrap(slot: PinKeySlot, secret: ByteArray): ByteArray? {
        deleteKey(slot)
        val privateKey = createKey(slot) ?: return null
        val blob = try {
            encrypt(privateKey, secret)
        } finally {
            CFRelease(privateKey)
        }
        if (blob == null) deleteKey(slot)
        return blob
    }

    /** ECIES with the public half of [privateKey], which needs no Enclave call. */
    private fun encrypt(privateKey: SecKeyRef, secret: ByteArray): ByteArray? {
        val publicKey = SecKeyCopyPublicKey(privateKey) ?: return null
        val plain = newSecretData(secret)
        val sealed = SecKeyCreateEncryptedData(publicKey, ALGORITHM, plain, null)
        zeroizeAndRelease(plain)
        CFRelease(publicKey)
        return sealed?.let { cfDataToBytes(it).also { _ -> CFRelease(it) } }
    }

    /**
     * The secret [wrap] sealed. A missing Enclave key, or a blob it refuses, means the secret is gone for good
     * (`lost`); anything else (a locked device, a cancelled prompt) may pass.
     */
    @Throws(SecretStoreException::class)
    fun unwrap(slot: PinKeySlot, blob: ByteArray): ByteArray {
        val privateKey =
            findKey(slot) ?: throw SecretStoreException(lost = true, message = "Secure Enclave key is gone")
        try {
            val cipher = newSecretData(blob)
            try {
                memScoped {
                    val error = alloc<CFErrorRefVar>()
                    val plain = SecKeyCreateDecryptedData(privateKey, ALGORITHM, cipher, error.ptr)
                    if (plain == null) {
                        val code = error.value?.let { CFErrorGetCode(it).toInt().also { _ -> CFRelease(error.value) } }
                        throw SecretStoreException(lost = code in LOST_CODES, message = "Secure Enclave decrypt: $code")
                    }
                    return cfDataToBytes(plain).also { CFRelease(plain) }
                }
            } finally {
                zeroizeAndRelease(cipher)
            }
        } finally {
            CFRelease(privateKey)
        }
    }

    /** Removes the Enclave key of [slot]; none is not an error. */
    fun deleteKey(slot: PinKeySlot) {
        withCfDictionary { query ->
            SecItemDelete(
                query.put(kSecClass, kSecClassKey).putBytes(kSecAttrApplicationTag, tag(slot)).ref,
            )
        }
    }

    private fun createKey(slot: PinKeySlot): SecKeyRef? {
        val accessControl = SecAccessControlCreateWithFlags(
            null,
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            kSecAccessControlPrivateKeyUsage,
            null,
        ) ?: return null
        val privateAttributes = CfDictionary()
        val attributes = CfDictionary()
        try {
            privateAttributes.putBoolean(kSecAttrIsPermanent, true)
                .putBytes(kSecAttrApplicationTag, tag(slot))
                .put(kSecAttrAccessControl, accessControl)
            attributes.put(kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
                .putInt(kSecAttrKeySizeInBits, KEY_BITS)
                .put(kSecAttrTokenID, kSecAttrTokenIDSecureEnclave)
                .put(kSecPrivateKeyAttrs, privateAttributes.ref)
            return SecKeyCreateRandomKey(attributes.ref, null)
        } finally {
            attributes.release()
            privateAttributes.release()
            CFRelease(accessControl)
        }
    }

    private fun findKey(slot: PinKeySlot): SecKeyRef? = withCfDictionary { query ->
        query.put(kSecClass, kSecClassKey)
            .put(kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
            .putBytes(kSecAttrApplicationTag, tag(slot))
            .putBoolean(kSecReturnRef, true)
            .put(kSecMatchLimit, kSecMatchLimitOne)
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query.ref, result.ptr)
            when (status) {
                errSecSuccess -> result.value as SecKeyRef?
                errSecItemNotFound -> null
                else -> throw SecretStoreException(lost = false, message = "Secure Enclave key lookup: $status")
            }
        }
    }

    private fun tag(slot: PinKeySlot): ByteArray = "$service.enclave.${slot.entryName()}".encodeToByteArray()

    private companion object {
        val ALGORITHM = kSecKeyAlgorithmECIESEncryptionCofactorX963SHA256AESGCM
        const val KEY_BITS = 256

        // errSecDecode (-26275) and errSecParam (-50): the blob does not belong to the key.
        val LOST_CODES = setOf(-26275, -50)
    }
}
