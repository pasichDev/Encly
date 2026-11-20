package com.pasich.encly.core.keystore

import com.pasich.encly.core.crypto.Crypto
import com.pasich.encly.core.security.PinFactorException
import com.pasich.encly.core.security.PinHardwareFactor
import com.pasich.encly.core.security.PinKeySlot
import com.pasich.encly.core.security.SensitiveDataCleaner

/**
 * [PinHardwareFactor] for iOS, with the property Android has: every PIN guess needs this device's secure hardware.
 *
 * Android computes `HMAC(keystoreKey, salt ‖ pin)` inside the Keystore. The Secure Enclave has no HMAC; it holds
 * only EC keys and answers ECDH. So the factor is an oblivious-PRF-shaped construction over the Enclave's key `d`
 * (a non-extractable P-256 key, one per [PinKeySlot], made by [PinEnclave]):
 * ```
 * Q  = hash_to_curve(domain ‖ slot ‖ data)       data = salt ‖ PIN, as the caller passes it  (PinPoint)
 * Z  = x(d · Q)                                   one ECDH inside the Secure Enclave, per guess
 * hw = HKDF-SHA-256(ikm = Z, salt = domain, info = domain ‖ data, 32 bytes)
 * ```
 * - Deterministic: the same slot key and `data` give the same `hw`, as the vault needs to open its PIN slot again.
 * - Needs the Enclave per guess: `Q` has no known discrete logarithm, so `Z = d·Q` cannot be computed from the
 *   public key, the Keychain or the vault file. A copy of everything on the phone except the Enclave tests no PINs;
 *   each guess made with the phone costs an Enclave call. Only someone who can run code in this process while it is
 *   unlocked can query the Enclave, and that guess rate is the Enclave's, not a GPU's.
 * - What stays in memory after an unlock: the point `Q` of the last real guess (it gives that PIN back by hashing every
 *   possible PIN to a point: 10^6 tries for six digits, no Enclave involved) and its agreement `Z` can survive in
 *   memory that cannot be wiped (Swift `Data`, the `SecKey` made for the peer point, the CFData of the
 *   agreement). A dump of this process taken after an unlock, together with
 *   the vault file, therefore holds the PIN and the hardware half of that one unlock without the Enclave. It gives
 *   nothing for any other guess.
 * - Not the same bytes as the desktop or Android factor for the same data (the vault is bound to one device anyway).
 *
 * Two constructions that look simpler were rejected because they let the PIN be searched without the Enclave:
 * - `Q = k·G` with `k` derived from the PIN: ECDH commutes, `d·(k·G) = k·(d·G)`, and `d·G` is the public key.
 * - A fixed peer key kept in the Keychain, with the PIN mixed in only after the agreement: the agreement is then a
 *   per-slot constant, and one capture of it (it passes through this process's memory) turns into an offline search.
 *
 * States, as on Android and the desktop:
 * - [ensureKey] makes the key when there is none and returns true then.
 * - [reset] replaces the key; slots sealed with the old one can no longer be opened.
 * - [mac] with no key fails with `PinFactorException(lost = true)`; any other failure (a locked device, the Enclave
 *   refusing) is `lost = false` and may pass.
 * - [delete] never throws.
 *
 * On a device the key is in the Secure Enclave and a device whose Enclave refuses to make one cannot set a PIN up
 * (`ensureKey`/`reset` fail with `lost = false`): there is no weaker fallback. Only the Simulator, which has no
 * Enclave, uses a software key (see [platformPinEnclave]). Every array this class holds (the point, the agreement,
 * the key material) is wiped after use; the copies the system and Swift keep (see above) are immutable and are not.
 */
public class IosPinFactor internal constructor(private val enclave: PinEnclave) : PinHardwareFactor {
    @Throws(PinFactorException::class)
    override fun ensureKey(slot: PinKeySlot): Boolean {
        try {
            if (enclave.exists(slot)) return false
            enclave.create(slot)
            return true
        } catch (e: SecretStoreException) {
            throw PinFactorException(lost = false, cause = e)
        }
    }

    @Throws(PinFactorException::class)
    override fun reset(slot: PinKeySlot) {
        try {
            enclave.create(slot)
        } catch (e: SecretStoreException) {
            throw PinFactorException(lost = false, cause = e)
        }
    }

    @Throws(PinFactorException::class)
    override fun mac(slot: PinKeySlot, data: ByteArray): ByteArray {
        val point = try {
            PinPoint.derive(slot, data)
        } catch (e: IllegalStateException) {
            throw PinFactorException(lost = false, cause = e)
        }
        try {
            val agreement = try {
                enclave.agree(slot, point)
            } catch (e: SecretStoreException) {
                throw PinFactorException(lost = e.lost, cause = e)
            } ?: throw PinFactorException(lost = true)
            return derive(agreement, data)
        } finally {
            SensitiveDataCleaner.clear(point)
        }
    }

    override fun delete(slot: PinKeySlot) {
        try {
            enclave.delete(slot)
        } catch (_: SecretStoreException) {
            // Documented: delete never throws.
        }
    }

    private fun derive(agreement: ByteArray, data: ByteArray): ByteArray {
        val info = KDF_INFO + data
        try {
            if (agreement.size != AGREEMENT_BYTES) throw PinFactorException(lost = false)
            return Crypto.hkdfSha256(agreement, KDF_INFO, info, HW_BYTES)
        } finally {
            SensitiveDataCleaner.clear(agreement)
            SensitiveDataCleaner.clear(info)
        }
    }

    public companion object {
        /** The factor on this device's Secure Enclave (on the Simulator, a software key). */
        public fun create(): IosPinFactor = IosPinFactor(platformPinEnclave(APPLICATION_ID))

        private val KDF_INFO = "encly.ios.pin-factor.v2/hw".encodeToByteArray()
        private const val HW_BYTES = 32
    }
}
