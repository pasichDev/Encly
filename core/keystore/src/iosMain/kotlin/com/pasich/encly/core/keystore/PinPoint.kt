package com.pasich.encly.core.keystore

import com.pasich.encly.core.crypto.Crypto
import com.pasich.encly.core.crypto.P256Points
import com.pasich.encly.core.security.PinKeySlot
import com.pasich.encly.core.security.SensitiveDataCleaner

/**
 * The point of the P-256 curve that a PIN guess is turned into before it goes to the Secure Enclave
 * (see [IosPinFactor]): a hash of the guess onto the curve, so that nobody, the app included, knows its discrete
 * logarithm. That is the whole point: with `Q = k·G` for a `k` derived from the PIN, the agreement
 * `d·Q = k·(d·G)` would need only the Enclave key's *public* half, and the PIN could be searched without the Enclave.
 *
 * "Try and increment": `x = SHA-256(domain ‖ 0 ‖ slot ‖ 0 ‖ data ‖ counter)` for counter 0, 1, 2, … until
 * CryptoKit finds a curve point with that abscissa ([P256Points.fromX], roughly every other x does), whose
 * result is the point `04 ‖ X ‖ Y`. The counter is one byte; 256 misses in a row have a probability of 2^-256.
 *
 * Not constant-time: how many tries a PIN needed shows in the time of a guess. Whoever can measure that inside this
 * process already sees the PIN. The point is as sensitive as the PIN (it falls to 10^6 SHA-256 calls): callers wipe
 * it and nothing stores or logs it.
 */
internal object PinPoint {
    private val DOMAIN = "encly.ios.pin-factor.v2/hash-to-point".encodeToByteArray()
    private const val SEPARATOR: Byte = 0
    private const val MAX_TRIES = 256

    /** The point of [data] (`salt ‖ PIN`, as the vault passes it) for [slot]. The caller wipes it. */
    fun derive(slot: PinKeySlot, data: ByteArray): ByteArray {
        val slotName = slot.entryName().encodeToByteArray()
        // One buffer for every try: only the counter at its end changes, and it is wiped once.
        val input = DOMAIN + SEPARATOR + slotName + SEPARATOR + data + 0.toByte()
        try {
            for (counter in 0 until MAX_TRIES) {
                pointFor(input, counter)?.let { return it }
            }
        } finally {
            SensitiveDataCleaner.clear(input)
        }
        error("no curve point in $MAX_TRIES tries")
    }

    /** The point of try [counter] (the last byte of [input]), or null when the hash is no abscissa of a point. */
    private fun pointFor(input: ByteArray, counter: Int): ByteArray? {
        input[input.lastIndex] = counter.toByte()
        val x = Crypto.sha256(input)
        try {
            return P256Points.fromX(x)
        } finally {
            SensitiveDataCleaner.clear(x)
        }
    }
}
