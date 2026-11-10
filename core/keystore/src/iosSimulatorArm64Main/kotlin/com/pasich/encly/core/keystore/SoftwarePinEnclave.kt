@file:OptIn(ExperimentalForeignApi::class)

package com.pasich.encly.core.keystore

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly

internal actual fun platformPinEnclave(service: String): PinEnclave = SoftwarePinEnclave(service)

/**
 * The Simulator has no Secure Enclave, so its key is an ordinary EC P-256 key in the Keychain
 * (`WhenUnlockedThisDeviceOnly`). The construction is the same, but nothing makes the key hard to extract from
 * the Mac that hosts the Simulator: this class is for development and for tests, and is not compiled for a device.
 */
internal class SoftwarePinEnclave(service: String) : SecKeyPinEnclave(service) {
    override val refusal: String = "software key refused"

    override fun configure(attributes: CfDictionary, privateAttributes: CfDictionary) {
        privateAttributes.put(kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
    }
}
