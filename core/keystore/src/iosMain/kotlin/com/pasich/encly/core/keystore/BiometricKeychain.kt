@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.pasich.encly.core.keystore

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRetain
import platform.LocalAuthentication.LAContext
import platform.Security.SecAccessControlCreateWithFlags
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecInteractionNotAllowed
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.errSecUserCanceled
import platform.Security.kSecAccessControlBiometryCurrentSet
import platform.Security.kSecAttrAccessControl
import platform.Security.kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecAttrSynchronizable
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnAttributes
import platform.Security.kSecReturnData
import platform.Security.kSecUseAuthenticationContext
import platform.Security.kSecValueData

/** Why [BiometricKeychain.read] gave no secret. */
public class BiometricKeychainException internal constructor(
    /** The user dismissed the prompt (or the system did, because the app left the screen). */
    public val cancelled: Boolean,
    /** The item is gone for good: it never existed, or the biometric set changed and the system dropped it. */
    public val gone: Boolean,
    status: Int,
) : Exception("biometric Keychain item: $status")

/**
 * The key behind the biometric unlock of the vault on iOS: one generic-password item whose access control is
 * `biometryCurrentSet`. The Secure Enclave releases the item's data only after a Face ID or Touch ID match for the
 * prompt of the [LAContext] given to [read], never on a plain "the prompt succeeded" answer; adding a finger or a
 * face (a change of the biometric set) makes the system drop the item, like Android's key that is invalidated by
 * biometric enrollment. The item is available only while a device passcode is set (`WhenPasscodeSetThisDeviceOnly`:
 * removing the passcode deletes it), never synchronized, never in a backup that is restored to another device.
 * There is no device-passcode fallback: the access control has no `devicePasscode` flag, the same as Android's
 * BIOMETRIC_STRONG only.
 *
 * What it holds is a random 32-byte wrap key, not the vault's data key: the data key is wrapped by it in the
 * vault's slot file, so neither the item nor the slot entry opens anything alone. The caller wipes what it reads.
 */
public class BiometricKeychain(private val service: String = APPLICATION_ID, private val account: String = ACCOUNT) {
    /**
     * Whether the item is there. Asks for its attributes only, which the system answers without a prompt; an item
     * that would need one is answered with `errSecInteractionNotAllowed`, which also means it exists.
     */
    public fun exists(): Boolean = withCfDictionary { query ->
        val context = LAContext().apply { interactionNotAllowed = true }
        itemQuery(query)
            .putBoolean(kSecReturnAttributes, true)
            .put(kSecMatchLimit, kSecMatchLimitOne)
            .putOwned(kSecUseAuthenticationContext, CFBridgingRetain(context))
        memScoped {
            val result = alloc<CFTypeRefVar>()
            when (SecItemCopyMatching(query.ref, result.ptr)) {
                errSecSuccess -> {
                    result.value?.let { CFRelease(it) }
                    true
                }

                errSecInteractionNotAllowed -> true

                else -> false
            }
        }
    }

    /** Stores [secret] under the biometric access control, replacing the item; false if the system refused. */
    public fun store(secret: ByteArray): Boolean {
        delete()
        return withCfDictionary { item ->
            val control = memScoped {
                val error = alloc<CFErrorRefVar>()
                SecAccessControlCreateWithFlags(
                    null,
                    kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly,
                    kSecAccessControlBiometryCurrentSet,
                    error.ptr,
                ).also { error.value?.let { CFRelease(it) } }
            } ?: return@withCfDictionary false
            itemQuery(item).putOwned(kSecAttrAccessControl, control).putBytes(kSecValueData, secret)
            SecItemAdd(item.ref, null) == errSecSuccess
        }
    }

    /**
     * The secret, after the biometric prompt of [context] was passed. Blocks until the user answers, so it must not
     * run on the main thread. Throws a [BiometricKeychainException] when there is no secret.
     */
    @kotlin.Throws(BiometricKeychainException::class)
    public fun read(context: LAContext): ByteArray = withCfDictionary { query ->
        itemQuery(query)
            .putBoolean(kSecReturnData, true)
            .put(kSecMatchLimit, kSecMatchLimitOne)
            .putOwned(kSecUseAuthenticationContext, CFBridgingRetain(context))
        memScoped {
            val result = alloc<CFTypeRefVar>()
            when (val status = SecItemCopyMatching(query.ref, result.ptr)) {
                errSecSuccess -> {
                    val data: CFDataRef = checkNotNull(result.value).reinterpret()
                    try {
                        cfDataToBytes(data)
                    } finally {
                        CFRelease(data)
                    }
                }

                errSecUserCanceled ->
                    throw BiometricKeychainException(cancelled = true, gone = false, status = status)

                errSecItemNotFound ->
                    throw BiometricKeychainException(cancelled = false, gone = true, status = status)

                else -> throw BiometricKeychainException(cancelled = false, gone = false, status = status)
            }
        }
    }

    /** Removes the item; never throws. */
    public fun delete() {
        withCfDictionary { query -> SecItemDelete(itemQuery(query).ref) }
    }

    private fun itemQuery(query: CfDictionary): CfDictionary = query
        .put(kSecClass, kSecClassGenericPassword)
        .putString(kSecAttrService, service)
        .putString(kSecAttrAccount, account)
        .putBoolean(kSecAttrSynchronizable, false)

    private companion object {
        const val ACCOUNT = "biometric-wrap-v2"
    }
}
