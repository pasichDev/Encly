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
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecKeyCopyKeyExchangeResult
import platform.Security.SecKeyCreateRandomKey
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyRef
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrApplicationTag
import platform.Security.kSecAttrIsPermanent
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecClass
import platform.Security.kSecClassKey
import platform.Security.kSecKeyAlgorithmECDHKeyExchangeStandard
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecPrivateKeyAttrs
import platform.Security.kSecReturnRef

/**
 * The P-256 key of a [PinKeySlot] that the PIN factor asks for one key agreement per PIN guess (see [IosPinFactor]).
 * The private half never leaves where it was made: on a device the Secure Enclave, which only hands out the
 * result of an agreement; on the Simulator, which has none, a software key in the Keychain
 * ([platformPinEnclave]). Failures are [SecretStoreException]s, `lost` only where the key can never be used again.
 */
internal interface PinEnclave {
    /** Whether the key of [slot] exists. */
    @Throws(SecretStoreException::class)
    fun exists(slot: PinKeySlot): Boolean

    /** Makes a new key for [slot], replacing the one it had. Refuses (never falls back to something weaker). */
    @Throws(SecretStoreException::class)
    fun create(slot: PinKeySlot)

    /** Removes the key of [slot]; none is not an error. */
    @Throws(SecretStoreException::class)
    fun delete(slot: PinKeySlot)

    /**
     * ECDH of the key of [slot] with the public point [peer] (ANSI X9.63, 65 bytes): the 32-byte x coordinate of
     * the shared point, or null when [slot] has no key. The caller wipes [peer] and the result.
     */
    @Throws(SecretStoreException::class)
    fun agree(slot: PinKeySlot, peer: ByteArray): ByteArray?
}

/**
 * [PinEnclave] over Security.framework keys that live in the Keychain, one permanent EC P-256 private key per slot,
 * found by its application tag. What differs between the Secure Enclave and the Simulator's software key is
 * only how the key is made ([configure]), which each platform source set supplies, so a device binary holds
 * no software-key code.
 */
internal abstract class SecKeyPinEnclave(private val service: String) : PinEnclave {
    /**
     * Adds what makes the key hardware or software (token, access control or accessibility) to the attributes of the
     * key and of its private half. The tag and permanence are set by the caller.
     */
    protected abstract fun configure(attributes: CfDictionary, privateAttributes: CfDictionary)

    /** What [create] says when the key cannot be made. */
    protected abstract val refusal: String

    @Throws(SecretStoreException::class)
    override fun exists(slot: PinKeySlot): Boolean {
        val key = findKey(slot) ?: return false
        CFRelease(key)
        return true
    }

    @Throws(SecretStoreException::class)
    override fun create(slot: PinKeySlot) {
        delete(slot)
        val privateAttributes = CfDictionary()
        val attributes = CfDictionary()
        try {
            privateAttributes.putBoolean(kSecAttrIsPermanent, true).putBytes(kSecAttrApplicationTag, tag(slot))
            attributes.put(kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
                .putInt(kSecAttrKeySizeInBits, KEY_BITS)
                .put(kSecPrivateKeyAttrs, privateAttributes.ref)
            configure(attributes, privateAttributes)
            memScoped {
                val error = alloc<CFErrorRefVar>()
                val key = SecKeyCreateRandomKey(attributes.ref, error.ptr)
                    ?: throw SecretStoreException(lost = false, message = "$refusal: ${errorCode(error)}")
                CFRelease(key)
            }
        } finally {
            attributes.release()
            privateAttributes.release()
        }
    }

    @Throws(SecretStoreException::class)
    override fun delete(slot: PinKeySlot) {
        val status = withCfDictionary { query ->
            SecItemDelete(query.put(kSecClass, kSecClassKey).putBytes(kSecAttrApplicationTag, tag(slot)).ref)
        }
        if (status != errSecSuccess && status != errSecItemNotFound) {
            throw SecretStoreException(lost = false, message = "key delete: $status")
        }
    }

    @Throws(SecretStoreException::class)
    override fun agree(slot: PinKeySlot, peer: ByteArray): ByteArray? {
        val key = findKey(slot) ?: return null
        try {
            return keyAgreement(key, peer)
        } finally {
            CFRelease(key)
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
            when (val status = SecItemCopyMatching(query.ref, result.ptr)) {
                errSecSuccess -> result.value as SecKeyRef?
                errSecItemNotFound -> null
                else -> throw SecretStoreException(lost = false, message = "key lookup: $status")
            }
        }
    }

    private fun tag(slot: PinKeySlot): ByteArray = "$service.pin-key-v2.${slot.entryName()}".encodeToByteArray()

    private companion object {
        const val KEY_BITS = 256
    }
}

/**
 * `SecKeyCopyKeyExchangeResult(privateKey, ecdhKeyExchangeStandard, peer)`: the x coordinate of `d · peer`, 32
 * bytes. It needs no Keychain, so the tests run it on a key made in memory. The CFData Apple returns is immutable
 * and cannot be wiped; the copy handed back is the caller's to wipe.
 */
@Throws(SecretStoreException::class)
internal fun keyAgreement(privateKey: SecKeyRef, peer: ByteArray): ByteArray = memScoped {
    val error = alloc<CFErrorRefVar>()
    val publicKey = peerKey(peer, error)
    val parameters = CfDictionary()
    try {
        val shared = SecKeyCopyKeyExchangeResult(
            privateKey,
            kSecKeyAlgorithmECDHKeyExchangeStandard,
            publicKey,
            parameters.ref,
            error.ptr,
        ) ?: throw SecretStoreException(lost = false, message = "key agreement: ${errorCode(error)}")
        try {
            cfDataToBytes(shared).also {
                if (it.size != AGREEMENT_BYTES) {
                    it.fill(0)
                    throw SecretStoreException(lost = false, message = "key agreement of ${it.size} bytes")
                }
            }
        } finally {
            CFRelease(shared)
        }
    } finally {
        parameters.release()
        CFRelease(publicKey)
    }
}

internal const val AGREEMENT_BYTES = 32

/** The public key of the X9.63 point [peer]; a point that is not on the curve is refused by the framework. */
private fun peerKey(peer: ByteArray, error: CFErrorRefVar): SecKeyRef {
    val attributes = CfDictionary()
    val data = newSecretData(peer)
    try {
        attributes.put(kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom).put(kSecAttrKeyClass, kSecAttrKeyClassPublic)
        return SecKeyCreateWithData(data, attributes.ref, error.ptr)
            ?: throw SecretStoreException(lost = false, message = "peer point: ${errorCode(error)}")
    } finally {
        attributes.release()
        zeroizeAndRelease(data)
    }
}

private fun errorCode(error: CFErrorRefVar): Long =
    error.value?.let { CFErrorGetCode(it).also { _ -> CFRelease(it) } } ?: 0
