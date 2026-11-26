package com.pasich.encly.testutil

import com.pasich.encly.core.security.KeyStoreProblem
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
 * (for every slot), [unavailable] a locked desktop keyring, [failingSlots] one for some slots only; [failingReset] a key that cannot be
 * generated. [delete] removes a key the way the system can, so [ensureKey] then reports a new
 * one. [calls] counts MACs, i.e. PIN guesses that reached the "hardware".
 */
internal class FakePinFactor : PinHardwareFactor {
    private val keys = mutableMapOf<PinKeySlot, ByteArray>()
    var lost = false
    var failing = false
    var failingReset = false

    /** The store of the keys is locked or not running (a desktop keyring): every MAC and reset fails as such. */
    var unavailable = false

    /** What [unavailable] says is missing. */
    var problem: KeyStoreProblem? = null

    /** The store answers but holds no key, and that does not prove the key is gone (a desktop keyring). */
    var notFound = false

    /** [prepare] fails as a store with this problem. */
    var prepareProblem: KeyStoreProblem? = null

    /** A MAC that waits on the user (a keyring prompt) until [abandonPending]; then fails as a dismissed prompt. */
    var promptOpen: java.util.concurrent.CountDownLatch? = null
    val asking = java.util.concurrent.CountDownLatch(1)
    var prepared = 0
        private set
    var abandoned = 0
        private set

    override val missingKeyMayReturn: Boolean get() = missingMayReturn
    var missingMayReturn = false

    /** Slots whose MAC fails like a transient Keystore error, while the others still work. */
    var failingSlots: Set<PinKeySlot> = emptySet()
    var calls = 0
        private set
    var resets = 0
        private set

    fun hasKey(slot: PinKeySlot): Boolean = slot in keys

    override fun ensureKey(slot: PinKeySlot): Boolean {
        val missing = slot !in keys
        if (missing) reset(slot)
        return missing
    }

    override fun reset(slot: PinKeySlot) {
        keys.remove(slot)
        if (unavailable) throw PinFactorException(lost = false, unavailable = true, problem = problem)
        if (failingReset) throw PinFactorException(lost = false)
        keys[slot] = ByteArray(32).also { SecureRandom().nextBytes(it) }
        lost = false
        resets++
    }

    override fun mac(slot: PinKeySlot, data: ByteArray): ByteArray {
        if (unavailable) throw PinFactorException(lost = false, unavailable = true, problem = problem)
        promptOpen?.let {
            asking.countDown()
            it.await(10, java.util.concurrent.TimeUnit.SECONDS)
            throw PinFactorException(lost = false, unavailable = true, problem = KeyStoreProblem.DISMISSED)
        }
        if (notFound) throw PinFactorException(lost = false, notFound = true)
        if (failing || slot in failingSlots) throw PinFactorException(lost = false)
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

    /** The key of [slot] as it is now, or null; to see that a new vault did not keep an old one. */
    fun keyOf(slot: PinKeySlot): List<Byte>? = keys[slot]?.toList()

    /** Puts [key] in [slot] as one a former vault left behind. */
    fun leave(slot: PinKeySlot, key: ByteArray) {
        keys[slot] = key
    }

    override fun prepare() {
        prepareProblem?.let { throw PinFactorException(lost = false, unavailable = true, problem = it) }
        prepared++
    }

    override fun abandonPending() {
        abandoned++
        promptOpen?.countDown()
    }
}

/** A [VaultStore] on a fresh temp file (deleted when the JVM exits). */
internal fun tempVaultStore(): VaultStore = VaultStore(tempVaultFile())

internal fun tempVaultFile(): File {
    val dir = Files.createTempDirectory("vault").toFile().apply { deleteOnExit() }
    return File(dir, "vault_state.bin").apply { deleteOnExit() }
}
