@file:OptIn(ExperimentalForeignApi::class)

package com.pasich.encly.core.keystore

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Security.SecAccessControlCreateWithFlags
import platform.Security.kSecAccessControlPrivateKeyUsage
import platform.Security.kSecAttrAccessControl
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrTokenID
import platform.Security.kSecAttrTokenIDSecureEnclave

internal actual fun platformPinEnclave(service: String): PinEnclave = SecureEnclavePinEnclave(service)

/**
 * The key is an EC P-256 private key created inside the Secure Enclave (`kSecAttrTokenIDSecureEnclave`): it cannot
 * be exported, not even by this process, and every use is a call into the Enclave. Access control is
 * `privateKeyUsage` only, with `WhenUnlockedThisDeviceOnly`: usable while the device is unlocked, never
 * synchronized, never in a backup restored to another device, and no biometry (a changed Face ID must not
 * invalidate the PIN). Removing the device passcode makes iOS drop the key, which reads as `lost`.
 */
internal class SecureEnclavePinEnclave(service: String) : SecKeyPinEnclave(service) {
    override val refusal: String = "Secure Enclave key refused (Encly never falls back to a software key on a device)"

    override fun configure(attributes: CfDictionary, privateAttributes: CfDictionary) {
        val accessControl = SecAccessControlCreateWithFlags(
            null,
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            kSecAccessControlPrivateKeyUsage,
            null,
        ) ?: throw SecretStoreException(lost = false, message = refusal)
        privateAttributes.putOwned(kSecAttrAccessControl, accessControl)
        attributes.put(kSecAttrTokenID, kSecAttrTokenIDSecureEnclave)
    }
}
