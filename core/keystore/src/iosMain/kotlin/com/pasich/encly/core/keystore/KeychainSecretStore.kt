@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.pasich.encly.core.keystore

import com.pasich.encly.core.crypto.Crypto
import com.pasich.encly.core.security.PinKeySlot
import com.pasich.encly.core.security.SensitiveDataCleaner
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecAttrSynchronizable
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * [SecretStore] on iOS: one generic-password item per [PinKeySlot] in the app's Keychain (service
 * `com.pasich.encly`, account `pin-factor-v1` or `pin-factor-v1-b`), readable only while the device is unlocked
 * (`kSecAttrAccessibleWhenUnlockedThisDeviceOnly`), so it is not in iCloud Keychain, not in a backup that is
 * restored to another device, and not readable in the background of a locked phone.
 *
 * What the item holds is a format byte and a payload:
 * - [FORMAT_KEYCHAIN]: the 32-byte secret itself. This is the only path on the Simulator and on a device
 *   without a usable Secure Enclave, and the path every test in this repository exercises.
 * - [FORMAT_ENCLAVE]: the secret encrypted to a Secure Enclave key by [SecureEnclaveWrapper]; decrypting needs
 *   the Enclave of this device. Chosen whenever the Enclave accepts the key, and recorded in the item, so a
 *   read always follows the path that wrote it and a secret never silently moves between them.
 *
 * Pass `enclave = null` to keep the secret in the Keychain only.
 */
internal class KeychainSecretStore(
    private val service: String = APPLICATION_ID,
    private val enclave: SecureEnclaveWrapper? = SecureEnclaveWrapper(service),
) : SecretStore {
    @Throws(SecretStoreException::class)
    override fun read(slot: PinKeySlot): ByteArray? {
        val record = copyRecord(slot) ?: return null
        try {
            if (record.isEmpty()) throw SecretStoreException(lost = true, message = "empty Keychain item")
            val payload = record.copyOfRange(1, record.size)
            try {
                return when (record[0]) {
                    FORMAT_KEYCHAIN -> payload.copyOf()

                    FORMAT_ENCLAVE -> enclave?.unwrap(slot, payload)
                        ?: throw SecretStoreException(lost = true, message = "no Secure Enclave for a wrapped secret")

                    else -> throw SecretStoreException(lost = true, message = "unknown Keychain item format")
                }
            } finally {
                SensitiveDataCleaner.clear(payload)
            }
        } finally {
            SensitiveDataCleaner.clear(record)
        }
    }

    @Throws(SecretStoreException::class)
    override fun create(slot: PinKeySlot): ByteArray {
        val secret = Crypto.randomBytes(SecretStore.SECRET_SIZE)
        try {
            val wrapped = enclave?.wrap(slot, secret)
            val record = if (wrapped != null) {
                byteArrayOf(FORMAT_ENCLAVE) + wrapped
            } else {
                byteArrayOf(FORMAT_KEYCHAIN) + secret
            }
            try {
                writeRecord(slot, record)
            } finally {
                SensitiveDataCleaner.clear(record)
            }
            return secret
        } catch (e: SecretStoreException) {
            SensitiveDataCleaner.clear(secret)
            throw e
        }
    }

    @Throws(SecretStoreException::class)
    override fun delete(slot: PinKeySlot) {
        enclave?.deleteKey(slot)
        deleteItem(slot)
    }

    private fun deleteItem(slot: PinKeySlot) {
        val status = withCfDictionary { query -> SecItemDelete(itemQuery(query, slot).ref) }
        if (status != errSecSuccess && status != errSecItemNotFound) {
            throw SecretStoreException(lost = false, message = "Keychain delete: $status")
        }
    }

    private fun copyRecord(slot: PinKeySlot): ByteArray? = withCfDictionary { query ->
        itemQuery(query, slot).putBoolean(kSecReturnData, true).put(kSecMatchLimit, kSecMatchLimitOne)
        memScoped {
            val result = alloc<CFTypeRefVar>()
            when (val status = SecItemCopyMatching(query.ref, result.ptr)) {
                errSecSuccess -> {
                    val data: CFDataRef = checkNotNull(result.value).reinterpret()
                    cfDataToBytes(data).also { CFRelease(data) }
                }

                errSecItemNotFound -> null

                else -> throw SecretStoreException(lost = false, message = "Keychain read: $status")
            }
        }
    }

    private fun writeRecord(slot: PinKeySlot, record: ByteArray) {
        // Replace, not update: an item left by an earlier secret must not survive a failed write.
        deleteItem(slot)
        val status = withCfDictionary { item ->
            itemQuery(item, slot)
                .put(kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
                .putBytes(kSecValueData, record)
            SecItemAdd(item.ref, null)
        }
        if (status != errSecSuccess) throw SecretStoreException(lost = false, message = "Keychain write: $status")
    }

    private fun itemQuery(query: CfDictionary, slot: PinKeySlot): CfDictionary = query
        .put(kSecClass, kSecClassGenericPassword)
        .putString(kSecAttrService, service)
        .putString(kSecAttrAccount, slot.entryName())
        .putBoolean(kSecAttrSynchronizable, false)

    /** Which path wrote the item of [slot] (a `FORMAT_` value), or null when there is none; for tests. */
    fun recordFormat(slot: PinKeySlot): Byte? = copyRecord(slot)?.let { record ->
        record.firstOrNull().also { SensitiveDataCleaner.clear(record) }
    }

    internal companion object {
        const val FORMAT_KEYCHAIN: Byte = 1
        const val FORMAT_ENCLAVE: Byte = 2
    }
}
