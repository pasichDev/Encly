package com.pasich.encly.core.keystore

import java.math.BigInteger
import java.security.GeneralSecurityException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The encrypted transfer of the Secret Service, played against itself: the client and the "service" are both ours. */
class SecretServiceSessionTest {
    @Test
    fun theKeyExchangeGivesBothSidesTheSameCipher() {
        repeat(ROUNDS) {
            val client = SecretServiceDh.create()
            val service = SecretServiceDh.create()
            assertEquals(KEY_SIZE, client.publicKey.size)
            assertEquals(KEY_SIZE, service.publicKey.size)
            val secret = ByteArray(SECRET_SIZE) { (it * 7 + 1).toByte() }

            client.cipher(service.publicKey).use { clientCipher ->
                service.cipher(client.publicKey).use { serviceCipher ->
                    val sealed = clientCipher.encrypt(secret)
                    assertContentEquals(secret, serviceCipher.decrypt(sealed.iv, sealed.encrypted))
                }
            }
        }
    }

    @Test
    fun aPublicKeyIsAlways128UnsignedBytesWhateverItsLeadingBits() {
        assertContentEquals(ByteArray(KEY_SIZE), unsigned(BigInteger.ZERO, KEY_SIZE))
        val one = unsigned(BigInteger.ONE, KEY_SIZE)
        assertEquals(1, one.last().toInt())
        assertTrue(one.dropLast(1).all { it == 0.toByte() })
        // A value whose top bit is set has a sign byte in BigInteger.toByteArray(); it is not sent.
        val high = BigInteger.ONE.shiftLeft(KEY_SIZE * 8 - 1)
        val bytes = unsigned(high, KEY_SIZE)
        assertEquals(KEY_SIZE, bytes.size)
        assertEquals(0x80, bytes[0].toInt() and 0xff)
        assertFailsWith<IllegalArgumentException> { unsigned(BigInteger.ONE.shiftLeft(KEY_SIZE * 8), KEY_SIZE) }
    }

    @Test
    fun theKeyIsHkdfSha256WithoutSaltAndInfoOverTheSharedSecret() {
        val shared = ByteArray(KEY_SIZE) { it.toByte() }
        // RFC 5869: PRK = HMAC(zeros(32), IKM); T(1) = HMAC(PRK, 0x01); the key is its first 16 bytes.
        val prk = hmac(ByteArray(SHA256_SIZE), shared)
        val expected = hmac(prk, byteArrayOf(1)).copyOf(AES_SIZE)

        assertContentEquals(expected, hkdfSha256NoSalt(shared, AES_SIZE))
        assertEquals(SHA256_SIZE, hkdfSha256NoSalt(shared, SHA256_SIZE).size)
        assertFailsWith<IllegalArgumentException> { hkdfSha256NoSalt(shared, SHA256_SIZE + 1) }
    }

    @Test
    fun theHkdfMatchesRfc5869TestCase3() {
        // Test case 3 of RFC 5869 (SHA-256, empty salt and info). Our salt is the RFC's default for none: zeros.
        val ikm = ByteArray(22) { 0x0b }
        val expected = "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8"
        val okm = hkdfSha256NoSalt(ikm, SHA256_SIZE)

        assertEquals(expected.take(SHA256_SIZE * 2), okm.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun aSecretOfAnyLengthSurvivesAndTheIvIsFreshEveryTime() {
        val cipher = SecretServiceCipher(ByteArray(AES_SIZE) { it.toByte() })
        for (size in listOf(0, 1, 15, 16, 17, SECRET_SIZE, 100)) {
            val secret = ByteArray(size) { it.toByte() }
            val first = cipher.encrypt(secret)
            val second = cipher.encrypt(secret)
            assertEquals(AES_SIZE, first.iv.size)
            // PKCS#7 always pads: the ciphertext is the next multiple of 16 above the length.
            assertEquals((size / AES_SIZE + 1) * AES_SIZE, first.encrypted.size)
            assertFalse(first.iv.contentEquals(second.iv))
            assertContentEquals(secret, cipher.decrypt(first.iv, first.encrypted))
        }
    }

    @Test
    fun aDamagedSecretOrIvIsRefusedNotMisread() {
        val cipher = SecretServiceCipher(ByteArray(AES_SIZE) { it.toByte() })
        val sealed = cipher.encrypt(ByteArray(SECRET_SIZE) { 9 })

        assertFailsWith<GeneralSecurityException> { cipher.decrypt(ByteArray(AES_SIZE - 1), sealed.encrypted) }
        assertFailsWith<GeneralSecurityException> { cipher.decrypt(sealed.iv, sealed.encrypted.copyOf(AES_SIZE - 1)) }
        val wrongKey = SecretServiceCipher(ByteArray(AES_SIZE) { 1 })
        // A wrong key almost always fails the padding check; if it does not, it still never returns the secret.
        val outcome = runCatching { wrongKey.decrypt(sealed.iv, sealed.encrypted) }
        assertTrue(outcome.isFailure || !outcome.getOrThrow().contentEquals(ByteArray(SECRET_SIZE) { 9 }))
    }

    @Test
    fun aPublicKeyOutsideTheGroupIsRefused() {
        val client = SecretServiceDh.create()
        val primeMinusOne = BigInteger(OAKLEY_PRIME_HEX, 16) - BigInteger.ONE

        for (bad in listOf(BigInteger.ZERO, BigInteger.ONE, primeMinusOne, primeMinusOne + BigInteger.ONE)) {
            assertFailsWith<GeneralSecurityException> { client.cipher(unsigned(bad, KEY_SIZE)) }
        }
        assertFailsWith<GeneralSecurityException> { client.cipher(ByteArray(0)) }
    }

    @Test
    fun theServersPublicKeyIsReadFromTheOutputOfOpenSession() {
        val key = ByteArray(KEY_SIZE) { it.toByte() }

        assertContentEquals(key, serverPublicKeyOf(org.freedesktop.dbus.types.Variant(key)))
        assertFailsWith<GeneralSecurityException> { serverPublicKeyOf(org.freedesktop.dbus.types.Variant("none")) }
    }

    @Test
    fun theGroupIsTheSecondOakleyGroupOfRfc2409() {
        val prime = BigInteger(OAKLEY_PRIME_HEX, 16)
        assertEquals(1024, prime.bitLength())
        // A safe prime: p and (p - 1) / 2 are both prime.
        assertTrue(prime.isProbablePrime(CERTAINTY))
        assertTrue(prime.shiftRight(1).isProbablePrime(CERTAINTY))
        assertNotEquals(BigInteger.ZERO, prime)
        // The public keys are elements of the group generated by 2.
        val key = BigInteger(1, SecretServiceDh.create().publicKey)
        assertTrue(key > BigInteger.ONE && key < prime)
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(data)
    }

    private companion object {
        const val ROUNDS = 25
        const val KEY_SIZE = 128
        const val AES_SIZE = 16
        const val SHA256_SIZE = 32
        const val SECRET_SIZE = 32
        const val CERTAINTY = 40

        // The prime of RFC 2409 section 6.2, written out here independently of the code under test.
        const val OAKLEY_PRIME_HEX =
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
                "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
                "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE65381FFFFFFFFFFFFFFFF"
    }
}
