package com.pasich.encly.core.keystore

/**
 * The [PinEnclave] of this build. Two actuals, one per iOS target, so the two never share a binary:
 * - `iosArm64` (a device): the Secure Enclave, and nothing else. Where it cannot make a key, [PinEnclave.create]
 *   fails; the PIN is never protected by a weaker key instead. Every iOS version the app supports (15+) runs on
 *   hardware with an Enclave.
 * - `iosSimulatorArm64`: a software key in the Keychain, because the Simulator has no Enclave. It exists so the
 *   app and its tests can run there; it protects nothing against someone who can read the Mac.
 */
internal expect fun platformPinEnclave(service: String): PinEnclave
