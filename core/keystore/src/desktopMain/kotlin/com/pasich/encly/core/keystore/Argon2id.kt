package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.SecretStretchException
import com.pasich.encly.core.security.SensitiveDataCleaner
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/** The Argon2id function of the desktop password: `(password, salt, memoryKib, passes, lanes) -> 32 bytes`. */
internal typealias Argon2Function = (ByteArray, ByteArray, Int, Int, Int) -> ByteArray

/**
 * Argon2id version 1.3 (RFC 9106) over Bouncy Castle, the password stretch of slot version 4.
 *
 * [stretch] is the production entry point: it refuses parameters outside the bounds below before anything is
 * allocated, so a damaged or hostile slot header can never make the app reserve gigabytes. [compute] is the
 * unbounded primitive; it exists so that the RFC 9106 vector (32 KiB, 4 lanes, a secret and associated data)
 * can be checked by a test without widening the production bounds. Nothing here logs an input.
 *
 * Wiped: the Bouncy Castle parameter copies of the salt, secret and associated data, every block of the Argon2
 * matrix (a block pool of this call's own), and the output on failure. Not wiped: the password array, which stays the
 * caller's, and Bouncy Castle's own temporaries that no API reaches (the working blocks of its `FillBlock`, the
 * Blake2b state and the initial hash `H0`), which stay for the garbage collector. An [OutOfMemoryError] in [stretch]
 * (the matrix is 64 MiB and up) becomes a [SecretStretchException]: the attempt could not be made, whatever the
 * password.
 */
internal object Argon2id {
    const val MIN_MEMORY_KIB = 65_536
    const val MAX_MEMORY_KIB = 1_048_576
    const val MIN_PASSES = 3
    const val MAX_PASSES = 10
    const val LANES = 1
    const val SALT_BYTES = 16
    const val OUTPUT_BYTES = 32

    /** True when [memoryKib], [passes] and [lanes] are acceptable for a production slot. */
    fun isWithinBounds(memoryKib: Int, passes: Int, lanes: Int): Boolean =
        memoryKib in MIN_MEMORY_KIB..MAX_MEMORY_KIB && passes in MIN_PASSES..MAX_PASSES && lanes == LANES

    /**
     * The 32-byte Argon2id output of [password] and [salt] under the given cost. Throws
     * [IllegalArgumentException] for a salt that is not 16 bytes or a cost outside the production bounds,
     * before [engine] is called. Throws [SecretStretchException] when the attempt could not be made for lack of memory.
     * The returned array is the caller's to wipe. [engine] is the seam of the tests; production uses the default.
     */
    @Throws(SecretStretchException::class)
    fun stretch(
        password: ByteArray,
        salt: ByteArray,
        memoryKib: Int,
        passes: Int,
        lanes: Int,
        engine: Argon2Function = production,
    ): ByteArray {
        require(salt.size == SALT_BYTES) { "Argon2id salt must be $SALT_BYTES bytes" }
        require(isWithinBounds(memoryKib, passes, lanes)) {
            "Argon2id cost outside the allowed bounds: m=$memoryKib, t=$passes, p=$lanes"
        }
        return failOnOutOfMemory { engine(password, salt, memoryKib, passes, lanes) }
    }

    /** The real Argon2id, for [stretch] and for the policy that does not take another engine (tests do). */
    val production: Argon2Function = ::deriveOutput

    /**
     * Argon2id v1.3 with [OUTPUT_BYTES] of output and no secret or associated data, and NO bounds: use
     * [stretch] for anything that comes from a slot.
     */
    private fun deriveOutput(password: ByteArray, salt: ByteArray, memoryKib: Int, passes: Int, lanes: Int): ByteArray =
        compute(password, salt, memoryKib, passes, lanes)

    /**
     * Argon2id v1.3 of [password] without any bound on the cost, with the optional [secret] and
     * [additional] data of RFC 9106. For known-answer tests; production code calls [stretch].
     */
    fun compute(
        password: ByteArray,
        salt: ByteArray,
        memoryKib: Int,
        passes: Int,
        lanes: Int,
        outputLength: Int = OUTPUT_BYTES,
        secret: ByteArray? = null,
        additional: ByteArray? = null,
    ): ByteArray {
        val output = ByteArray(outputLength)
        val pool = WipingBlockPool()
        val builder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(passes)
            .withMemoryAsKB(memoryKib)
            .withParallelism(lanes)
            .withSalt(salt)
            .withBlockPool(pool)
        if (secret != null) builder.withSecret(secret)
        if (additional != null) builder.withAdditional(additional)
        val parameters = builder.build()
        var succeeded = false
        try {
            Argon2BytesGenerator().apply { init(parameters) }.generateBytes(password, output)
            succeeded = true
            return output
        } finally {
            parameters.clear()
            pool.wipe()
            if (!succeeded) SensitiveDataCleaner.clear(output)
        }
    }
}

/**
 * Runs [block], and turns an [OutOfMemoryError] into a [SecretStretchException]: the attempt could not be made, which
 * says nothing about the password. The exception carries no input.
 */
internal inline fun <T> failOnOutOfMemory(block: () -> T): T = try {
    block()
} catch (e: OutOfMemoryError) {
    throw SecretStretchException(e)
}

/**
 * The block pool of one Argon2id run. The generator takes its whole matrix (64 MiB and up) from the pool; the
 * matrix is derived from the password, and Bouncy Castle's own pool leaves it to the garbage collector, so this
 * one remembers every block and zeroes them all when the run ends, whichever way it ends.
 */
private class WipingBlockPool : Argon2BytesGenerator.BlockPool {
    private val blocks = ArrayList<Argon2BytesGenerator.Block>()

    override fun allocate(): Argon2BytesGenerator.Block = Argon2BytesGenerator.Block().also(blocks::add)

    override fun deallocate(block: Argon2BytesGenerator.Block) {
        // Wiped once, by wipe(): the generator hands every block back at the end of a successful run.
    }

    fun wipe() {
        for (block in blocks) block.clear()
        blocks.clear()
    }
}
