@file:OptIn(ExperimentalForeignApi::class)

package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.PinFactorException
import com.pasich.encly.core.security.PinHardwareFactor
import com.pasich.encly.core.security.PinKeySlot
import com.pasich.encly.core.security.SensitiveDataCleaner
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.kCCHmacAlgSHA256

/**
 * [PinHardwareFactor] for iOS. The contract is the one of the Android Keystore and the desktop factors:
 * `hw = HMAC-SHA256(deviceSecret, data)`, where the caller passes `data = salt ‖ pin`, so a vault made on one
 * platform derives the same PIN key from the same secret.
 *
 * The `deviceSecret` is 32 random bytes from the platform CSPRNG, created once per slot and kept only in the
 * Keychain of this device, available while it is unlocked, never synced or restored onto another device. Where
 * the Secure Enclave accepts it, the Keychain holds the secret only encrypted to an Enclave key (see
 * [KeychainSecretStore]); otherwise (the Simulator, an old device) it holds the secret itself, which is as safe
 * as the Keychain and the passcode. The HMAC is CommonCrypto's, in this process: the Enclave only supports EC
 * keys, so it protects the secret at rest rather than computing the MAC.
 *
 * States, as on the desktop:
 * - [ensureKey] creates the secret when there is none and returns true then. A secret that is there but cannot
 *   be read for good counts as existing (like an invalidated Android key): [mac] reports it as lost and [reset]
 *   replaces it.
 * - [mac] with no secret, or one of the wrong size, fails with `PinFactorException(lost = true)`; any other
 *   failure of the store (locked device, I/O) is `lost = false` and may pass.
 * - [delete] never throws.
 *
 * Every copy of the secret this class controls is wiped after use.
 */
public class IosPinFactor internal constructor(private val store: SecretStore) : PinHardwareFactor {
    @Suppress("ReturnCount") // Exists, unreadable (also exists), or created.
    @Throws(PinFactorException::class)
    override fun ensureKey(slot: PinKeySlot): Boolean {
        val existing = try {
            store.read(slot)
        } catch (e: SecretStoreException) {
            if (e.lost) return false
            throw PinFactorException(lost = false, cause = e)
        }
        if (existing != null) {
            SensitiveDataCleaner.clear(existing)
            return false
        }
        reset(slot)
        return true
    }

    @Throws(PinFactorException::class)
    override fun reset(slot: PinKeySlot) {
        try {
            store.delete(slot)
            SensitiveDataCleaner.clear(store.create(slot))
        } catch (e: SecretStoreException) {
            throw PinFactorException(lost = false, cause = e)
        }
    }

    @Throws(PinFactorException::class)
    override fun mac(slot: PinKeySlot, data: ByteArray): ByteArray {
        val secret = try {
            store.read(slot)
        } catch (e: SecretStoreException) {
            throw PinFactorException(lost = e.lost, cause = e)
        } ?: throw PinFactorException(lost = true)
        try {
            if (secret.size != SecretStore.SECRET_SIZE) throw PinFactorException(lost = true)
            return hmacSha256(secret, data)
        } finally {
            SensitiveDataCleaner.clear(secret)
        }
    }

    override fun delete(slot: PinKeySlot) {
        try {
            store.delete(slot)
        } catch (_: SecretStoreException) {
            // Documented: delete never throws.
        }
    }

    public companion object {
        /** The factor on this device's Keychain. */
        public fun create(): IosPinFactor = IosPinFactor(KeychainSecretStore())
    }
}

internal fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val mac = ByteArray(CC_SHA256_DIGEST_LENGTH)
    // An empty array has no first element to point at; CommonCrypto takes a null pointer with a length of 0.
    key.usePinnedOrNull { keyPointer ->
        data.usePinnedOrNull { dataPointer ->
            mac.usePinned {
                CCHmac(
                    kCCHmacAlgSHA256,
                    keyPointer,
                    key.size.convert(),
                    dataPointer,
                    data.size.convert(),
                    it.addressOf(0),
                )
            }
        }
    }
    return mac
}

private inline fun <R> ByteArray.usePinnedOrNull(
    block: (kotlinx.cinterop.CPointer<kotlinx.cinterop.ByteVar>?) -> R,
): R = if (isEmpty()) block(null) else usePinned { block(it.addressOf(0).reinterpret()) }
