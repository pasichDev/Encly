package com.pasich.encly.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** A version 4 header that does not fit the 25 bytes of the format is refused when written, never truncated. */
class SecretSlotsHeaderBoundsTest {
    private val dek = ByteArray(32) { it.toByte() }
    private val kek = ByteArray(32) { (it + 100).toByte() }
    private val salt = ByteArray(16) { (it * 3).toByte() }

    private fun header(
        version: Byte = SlotHeader.PASSWORD_VERSION,
        kdf: Int = 2,
        factor: Int = 1,
        memory: Long = 65_536L,
        passes: Int = 3,
        lanes: Int = 1,
        salt: ByteArray = this.salt,
    ) = SlotHeader(version, kdf, factor, memory, passes, lanes, salt)

    @Test
    fun theLargestValuesOfEveryFieldFit() {
        val largest = header(kdf = 255, factor = 255, memory = 0xFFFF_FFFFL, passes = 255, lanes = 255)
        val slot = SecretSlots.seal(dek, kek, largest)

        assertEquals(85, slot.size)
        assertEquals(listOf(4, -1, -1, -1, -1, -1, -1, -1, -1), slot.copyOf(9).map { it.toInt() })
    }

    @Test
    fun theSmallestValuesOfEveryFieldFit() {
        val slot = SecretSlots.seal(dek, kek, header(kdf = 0, factor = 0, memory = 0, passes = 0, lanes = 0))

        assertEquals(listOf(4, 0, 0, 0, 0, 0, 0, 0, 0), slot.copyOf(9).map { it.toInt() })
    }

    @Test
    fun aFieldOneBeyondItsRangeIsRefused() {
        val refused = mapOf(
            "kdf 256" to header(kdf = 256),
            "kdf -1" to header(kdf = -1),
            "factor 256" to header(factor = 256),
            "factor -1" to header(factor = -1),
            "memory 2^32" to header(memory = 0x1_0000_0000L),
            "memory -1" to header(memory = -1),
            "passes 256" to header(passes = 256),
            "passes -1" to header(passes = -1),
            "lanes 256" to header(lanes = 256),
            "lanes -1" to header(lanes = -1),
            "salt 15" to header(salt = ByteArray(15)),
            "salt 17" to header(salt = ByteArray(17)),
        )

        for ((name, bad) in refused) {
            assertFailsWith<IllegalArgumentException>(name) { SecretSlots.seal(dek, kek, bad) }
            assertFailsWith<IllegalArgumentException>(name) { SecretSlots.sealWipe(kek, bad) }
        }
    }

    @Test
    fun aVersionOfNeitherFormatIsRefusedByEveryWriter() {
        val unknown = header(version = 5)

        assertFailsWith<IllegalArgumentException> { SecretSlots.seal(dek, kek, unknown) }
        assertFailsWith<IllegalArgumentException> { SecretSlots.sealWipe(kek, unknown) }
        assertFailsWith<IllegalArgumentException> { SecretSlots.sealDuress(dek, kek, unknown) }
        assertFailsWith<IllegalArgumentException> { SecretSlots.openDuress(ByteArray(61), kek, unknown) }
        assertFailsWith<IllegalArgumentException> { SecretSlots.opensWipe(ByteArray(61), kek, unknown) }
    }
}
