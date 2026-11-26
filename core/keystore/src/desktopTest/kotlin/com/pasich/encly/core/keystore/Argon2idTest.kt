package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.SecretStretchException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Argon2idTest {
    private val salt = ByteArray(16) { it.toByte() }
    private val password = "correct horse battery staple".toByteArray(Charsets.UTF_8)

    @Test
    fun rfc9106Section53VectorWithSecretAndAssociatedData() {
        val tag = Argon2id.compute(
            password = ByteArray(32) { 0x01 },
            salt = ByteArray(16) { 0x02 },
            memoryKib = 32,
            passes = 3,
            lanes = 4,
            outputLength = 32,
            secret = ByteArray(8) { 0x03 },
            additional = ByteArray(12) { 0x04 },
        )

        assertEquals("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659", tag.toHex())
    }

    /**
     * Confirmed by two implementations: Bouncy Castle 1.86 and OpenSSL 4.1.0, with
     * `openssl kdf -keylen 32 -kdfopt pass:'correct horse battery staple'
     * -kdfopt hexsalt:000102030405060708090a0b0c0d0e0f -kdfopt iter:3 -kdfopt memcost:65536
     * -kdfopt lanes:1 -kdfopt threads:1 -binary ARGON2ID`.
     */
    @Test
    fun productionParametersVector() {
        val key = Argon2id.stretch(password, salt, 65_536, 3, 1)

        assertEquals("0d1a3c6523c8f06e4e0af9c515aa5b5448cfebd6838f2d52c3d8b6ef8ddc3c2e", key.toHex())
    }

    @Test
    fun theInputsAreLeftUntouched() {
        val pw = password.copyOf()
        val s = salt.copyOf()
        val secret = ByteArray(8) { 0x03 }

        Argon2id.compute(pw, s, 32, 3, 1, secret = secret)

        assertContentEquals(password, pw)
        assertContentEquals(salt, s)
        assertContentEquals(ByteArray(8) { 0x03 }, secret)
    }

    @Test
    fun aRunTheGeneratorRefusesFailsAndLeavesNoOutputBehind() {
        // Fewer than four bytes of output is refused by Argon2 itself, after the matrix pool and the parameters exist.
        assertFails { Argon2id.compute(password, salt, 32, 3, 1, outputLength = 3) }
        // The failure did not poison the next run.
        assertEquals(32, Argon2id.compute(password, salt, 32, 3, 1).size)
    }

    @Test
    fun theBoundsAreInclusiveAndAcceptedParametersReachTheEngine() {
        val calls = mutableListOf<List<Int>>()
        val engine: Argon2Function = { _, _, m, t, p ->
            calls += listOf(m, t, p)
            ByteArray(32)
        }

        Argon2id.stretch(password, salt, 65_536, 3, 1, engine)
        Argon2id.stretch(password, salt, 1_048_576, 10, 1, engine)

        assertEquals(listOf(listOf(65_536, 3, 1), listOf(1_048_576, 10, 1)), calls)
    }

    @Test
    fun everyOutOfRangeParameterIsRefusedBeforeTheEngineIsCalled() {
        var calls = 0
        val engine: Argon2Function = { _, _, _, _, _ ->
            calls++
            ByteArray(32)
        }
        val refused = listOf(
            Triple(65_535, 3, 1),
            Triple(1_048_577, 3, 1),
            Triple(Int.MAX_VALUE, 3, 1),
            Triple(0, 3, 1),
            Triple(-1, 3, 1),
            Triple(65_536, 2, 1),
            Triple(65_536, 11, 1),
            Triple(65_536, 0, 1),
            Triple(65_536, -3, 1),
            Triple(65_536, 3, 0),
            Triple(65_536, 3, 2),
            Triple(65_536, 3, 4),
            Triple(65_536, 3, -1),
        )

        for ((m, t, p) in refused) {
            assertFailsWith<IllegalArgumentException>("m=$m t=$t p=$p") {
                Argon2id.stretch(password, salt, m, t, p, engine)
            }
        }

        assertEquals(0, calls)
    }

    @Test
    fun aSaltOfAnotherSizeIsRefusedBeforeTheEngineIsCalled() {
        var calls = 0
        val engine: Argon2Function = { _, _, _, _, _ ->
            calls++
            ByteArray(32)
        }

        for (size in listOf(0, 8, 15, 17, 32)) {
            assertFailsWith<IllegalArgumentException>("salt $size") {
                Argon2id.stretch(password, ByteArray(size), 65_536, 3, 1, engine)
            }
        }

        assertEquals(0, calls)
    }

    @Test
    fun isWithinBoundsMatchesTheProductionRange() {
        assertTrue(Argon2id.isWithinBounds(65_536, 3, 1))
        assertTrue(Argon2id.isWithinBounds(1_048_576, 10, 1))
        assertFalse(Argon2id.isWithinBounds(65_536, 3, 2))
    }

    @Test
    fun outOfMemoryBecomesAFailedAttemptNotAnError() {
        val oom = OutOfMemoryError("simulated")

        val failure = assertFailsWith<SecretStretchException> { failOnOutOfMemory { throw oom } }

        assertEquals(oom, failure.cause)
        assertIs<OutOfMemoryError>(failure.cause)
    }

    @Test
    fun anOutOfMemoryOfTheEngineIsAFailedAttemptAndLeavesNothingToWipeHere() {
        val engine: Argon2Function = { _, _, _, _, _ -> throw OutOfMemoryError("simulated") }

        val failure = assertFailsWith<SecretStretchException> { Argon2id.stretch(password, salt, 65_536, 3, 1, engine) }

        assertIs<OutOfMemoryError>(failure.cause)
    }

    @Test
    fun theDefaultEngineIsTheProductionOne() {
        assertEquals(
            Argon2id.production(password, salt, 65_536, 3, 1).toHex(),
            Argon2id.stretch(password, salt, 65_536, 3, 1).toHex(),
        )
    }

    @Test
    fun anotherFailureIsNotMappedToAFailedAttempt() {
        assertFailsWith<IllegalStateException> { failOnOutOfMemory { error("boom") } }
    }
}
