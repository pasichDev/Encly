package com.pasich.encly.testutil

import com.pasich.encly.core.security.LockoutClock
import com.pasich.encly.core.security.PinFactorException
import com.pasich.encly.core.security.PinHardwareFactor
import com.pasich.encly.core.security.PinKeySlot
import com.pasich.encly.core.security.VaultStore
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * A [PinHardwareFactor] with software HMAC keys standing in for the Keystore ones, one per
 * [PinKeySlot]. [lost] and [failing] simulate an invalidated key and a transient Keystore error
 * (for every slot); [failingReset] a key that cannot be generated. [calls] counts MACs, i.e.
 * PIN guesses that reached the "hardware".
 */
internal class FakePinFactor : PinHardwareFactor {
    private val keys = mutableMapOf<PinKeySlot, ByteArray>()
    var lost = false
    var failing = false
    var failingReset = false
    var calls = 0
        private set
    var resets = 0
        private set

    fun hasKey(slot: PinKeySlot): Boolean = slot in keys

    override fun ensureKey(slot: PinKeySlot) {
        if (slot !in keys) reset(slot)
    }

    override fun reset(slot: PinKeySlot) {
        keys.remove(slot)
        if (failingReset) throw PinFactorException(lost = false)
        keys[slot] = ByteArray(32).also { SecureRandom().nextBytes(it) }
        lost = false
        resets++
    }

    override fun mac(slot: PinKeySlot, data: ByteArray): ByteArray {
        if (failing) throw PinFactorException(lost = false)
        val current = keys[slot]
        if (lost || current == null) throw PinFactorException(lost = true)
        calls++
        return Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(current, "HmacSHA256"))
            doFinal(data)
        }
    }

    override fun delete(slot: PinKeySlot) {
        keys.remove(slot)
    }
}

/** A [LockoutClock] the test moves by hand. */
internal class FakeLockoutClock(var elapsed: Long = 1_000_000L, var boot: Int = 1) : LockoutClock {
    override fun elapsedRealtime(): Long = elapsed
    override fun bootCount(): Int = boot
}

/** A [VaultStore] on a fresh temp file (deleted when the JVM exits). */
internal fun tempVaultStore(): VaultStore = VaultStore(tempVaultFile())

internal fun tempVaultFile(): File {
    val dir = Files.createTempDirectory("vault").toFile().apply { deleteOnExit() }
    return File(dir, "vault_state.bin").apply { deleteOnExit() }
}
