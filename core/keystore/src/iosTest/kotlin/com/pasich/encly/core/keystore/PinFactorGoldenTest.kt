@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.pasich.encly.core.keystore

import com.pasich.encly.core.crypto.P256Points
import com.pasich.encly.core.security.PinFactorException
import com.pasich.encly.core.security.PinKeySlot
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.CoreFoundation.CFErrorRefVar
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyRef
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPrivate
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The real construction, end to end, with the one thing a test cannot have swapped out: the key agreement is
 * Security.framework's ECDH, on keys made in memory from fixed scalars (no Keychain, so this runs in the plain test
 * executable). The expected values come from a separate computation of the same specification with CryptoKit's
 * `P256.KeyAgreement` and `HKDF`, so they also catch a change in how the system decodes a point or runs ECDH.
 */
class PinFactorGoldenTest {
    private val salt = ByteArray(16) { (0xA0 + it).toByte() }
    private val data = salt + "482913".encodeToByteArray()

    private val enclave = InMemoryKeyEnclave(
        PinKeySlot.A to KEY_1,
        PinKeySlot.B to KEY_1,
    )
    private val factor = IosPinFactor(enclave)

    @Test
    fun theFactorIsTheSpecifiedHkdfOfTheEnclavesAgreement() {
        assertEquals(
            "7f60a4188104296bd29f6c95dbeea5b127da38315a7e64ea0b5a8a740ac8ce4c",
            factor.mac(PinKeySlot.A, data).toHex(),
        )
        assertEquals(
            "b761bf14ee6f83a64d392142a6d49becc99451960f04bdd70186a9650ba3e429",
            factor.mac(PinKeySlot.A, salt + "482914".encodeToByteArray()).toHex(),
        )
    }

    @Test
    fun theSlotIsPartOfThePointSoTwoSlotsWithOneKeyStillDiffer() {
        assertEquals(
            "7f3b60b13c513cdc754ab0dac445dfbc6f6ccd0b6b3c93a3ca12233f25f16275",
            factor.mac(PinKeySlot.B, data).toHex(),
        )
    }

    @Test
    fun anotherKeyGivesAnotherFactorForTheSamePin() {
        val other = IosPinFactor(InMemoryKeyEnclave(PinKeySlot.A to KEY_2))

        assertEquals(
            "cc9991a8790aeeb1460fbef5153ffb43bf0c004a0732936e35a803d23b5e3811",
            other.mac(PinKeySlot.A, data).toHex(),
        )
    }

    @Test
    fun theSamePinAndKeyAlwaysGiveTheSameFactor() {
        assertContentEquals(factor.mac(PinKeySlot.A, data), factor.mac(PinKeySlot.A, data))
    }

    @Test
    fun thePointOfAPinHasTheDocumentedAbscissaAndIsOnTheCurve() {
        val point = PinPoint.derive(PinKeySlot.A, data)

        assertEquals(65, point.size)
        assertEquals(4, point[0].toInt())
        // The y of the pair is CryptoKit's choice and does not matter to an ECDH; the x is fixed by the hash.
        assertEquals(
            "f90b5e4c28b108e58da861a893c84bbd4cb15a0af9c6f981f03a485e7a876b12",
            point.copyOfRange(1, 33).toHex(),
        )
        assertNotNull(P256Points.fromX(point.copyOfRange(1, 33)))
    }

    @Test
    fun pointsOfPinsOneDigitApartAreUnrelated() {
        // The point is a hash, not k·G for a k derived from the PIN (then d·Q = k·(d·G) would follow from the
        // public key alone); the golden x above pins the hashing, this checks that the PIN reaches the point.
        val a = PinPoint.derive(PinKeySlot.A, data)
        val b = PinPoint.derive(PinKeySlot.A, salt + "482914".encodeToByteArray())

        assertFalse(a.copyOfRange(1, 33).contentEquals(b.copyOfRange(1, 33)))
    }

    @Test
    fun aPointOffTheCurveIsRefusedByTheAgreementAndIsNotLost() {
        val key = privateKey(KEY_1)
        val failure = assertFailsWith<SecretStoreException> { keyAgreement(key, ByteArray(65).also { it[0] = 4 }) }

        assertFalse(failure.lost)
    }

    @Test
    fun anUnknownSlotIsLost() {
        val onlyA = IosPinFactor(InMemoryKeyEnclave(PinKeySlot.A to KEY_1))

        assertTrue(assertFailsWith<PinFactorException> { onlyA.mac(PinKeySlot.B, data) }.lost)
    }
}

/** [PinEnclave] whose keys are Security.framework software keys made in memory (never in the Keychain). */
internal class InMemoryKeyEnclave(vararg keys: Pair<PinKeySlot, String>) : PinEnclave {
    private val keys = keys.associate { (slot, x963) -> slot to privateKey(x963) }.toMutableMap()

    override fun exists(slot: PinKeySlot): Boolean = slot in keys

    override fun create(slot: PinKeySlot) = throw UnsupportedOperationException()

    override fun delete(slot: PinKeySlot) {
        keys.remove(slot)
    }

    override fun agree(slot: PinKeySlot, peer: ByteArray): ByteArray? = keys[slot]?.let { keyAgreement(it, peer) }
}

/** A private key from `04 ‖ X ‖ Y ‖ K` in hex, as CryptoKit's `x963Representation` writes it. */
internal fun privateKey(x963Hex: String): SecKeyRef {
    val bytes = ByteArray(x963Hex.length / 2) { x963Hex.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
    val attributes = CfDictionary()
    val data = newSecretData(bytes)
    try {
        attributes.put(kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom).put(kSecAttrKeyClass, kSecAttrKeyClassPrivate)
        return memScoped {
            val error = alloc<CFErrorRefVar>()
            checkNotNull(SecKeyCreateWithData(data, attributes.ref, error.ptr)) { "test key refused" }
        }
    } finally {
        attributes.release()
        zeroizeAndRelease(data)
    }
}

/** The scalar 0x0102..20 and the scalar 0xFEFD..DF, as `04 ‖ X ‖ Y ‖ K`. */
private const val KEY_1 =
    "04515c3d6eb9e396b904d3feca7f54fdcd0cc1e997bf375dca515ad0a6c3b4035f4536be3a50f318fbf9a5475902a221502bef0d57e08c53b2cc0a56f17d9f93540102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20"
private const val KEY_2 =
    "04f6485850ce56925f57afe2cb80c91d65c02d3c7a2ab114f4a04cb1e6c08dd250680d456025bad34af7d6a7092bb1a3acb95613e5723286108e823646fc6df9c8fefdfcfbfaf9f8f7f6f5f4f3f2f1f0efeeedecebeae9e8e7e6e5e4e3e2e1e0df"
