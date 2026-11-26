package com.pasich.encly.testutil

import com.pasich.encly.core.security.SecretKind
import com.pasich.encly.core.security.SecretRule
import com.pasich.encly.core.security.SecretStretchException
import com.pasich.encly.core.security.SlotHeader
import com.pasich.encly.core.security.UnlockSecretPolicy

/**
 * TEST ONLY: a password-like [UnlockSecretPolicy] for slot version 4 with a cheap, deterministic stretch (an HMAC),
 * standing in for Argon2id, which arrives as a desktop-only dependency in a later step. It is not a password policy
 * to ship: no Unicode normalization, no surrogate check, and the stretch is as weak as a hash. What it does like the
 * real one: the v4 layout and HKDF infos, the device-factor input, the acceptance rule for the header
 * (memory 65 536..1 048 576 KiB, 3..10 passes, one lane, Argon2id, the OS-store factor), and the set rule of a
 * password (12 or more characters, not digits only).
 *
 * It counts what it was asked to do ([stretchCalls], [factorInputCalls]) and keeps every canonical copy it handed
 * out ([canonicals]), so a test can see that nothing was derived for a slot that was not accepted and that every
 * secret was wiped.
 */
internal class FakeV4PasswordPolicy : UnlockSecretPolicy {
    var stretchCalls = 0
        private set
    var factorInputCalls = 0
        private set
    val canonicals = mutableListOf<CharArray>()

    /** The stretch fails like an Argon2id that ran out of memory: [SecretStretchException] (after it was counted). */
    var failStretch = false

    /** The stretch fails with this unexpected error (a bug in a policy), after it was counted. */
    var failStretchWith: Throwable? = null

    /** The costs written into new slots; a test sets them to write a slot the policy then accepts or refuses. */
    var memoryKiB = 65_536L
    var passes = 3
    var lanes = 1
    var kdfId = SlotHeader.KDF_ARGON2ID
    var factorId = SlotHeader.FACTOR_OS_STORE_HMAC

    override val kind = SecretKind.PASSWORD
    override val slotVersion = SlotHeader.PASSWORD_VERSION
    override val chargesForUnusableSlot = false
    override val maxTypedUnits = MAX_UNLOCK_LENGTH
    override val kekInfo = "encly/secret/kek/v4".encodeToByteArray()
    override val wipeInfo = "encly/secret/wipe/v4".encodeToByteArray()
    override val decoyInfo = "encly/secret/decoy/v4".encodeToByteArray()

    override fun canonical(typed: CharArray): CharArray? =
        if (typed.isEmpty() || typed.size > MAX_UNLOCK_LENGTH) null else typed.copyOf().also { canonicals += it }

    override fun mayBeSet(canonical: CharArray): SecretRule = when {
        canonical.size < MIN_SET_LENGTH -> SecretRule.TOO_SHORT
        canonical.all { it in '0'..'9' } -> SecretRule.TOO_SIMPLE
        else -> SecretRule.OK
    }

    override fun newHeader(salt: ByteArray): SlotHeader =
        SlotHeader(slotVersion, kdfId, factorId, memoryKiB, passes, lanes, salt)

    @Throws(SecretStretchException::class)
    override fun stretch(canonical: CharArray, header: SlotHeader): ByteArray {
        stretchCalls++
        failStretchWith?.let { throw it }
        if (failStretch) throw SecretStretchException(OutOfMemoryError("simulated"))
        // Never sized by the header: whatever it asks for, the work here is one HMAC.
        val key = header.salt + "fake-stretch".encodeToByteArray() + header.memoryKiB.toString().encodeToByteArray() +
            header.passes.toString().encodeToByteArray()
        return hmacSha256(key, canonical.concatToString().encodeToByteArray())
    }

    override fun factorInput(canonical: CharArray, salt: ByteArray): ByteArray {
        factorInputCalls++
        return "encly/secret/hw/v4".encodeToByteArray() + salt + canonical.concatToString().encodeToByteArray()
    }

    override fun accepts(header: SlotHeader): Boolean = header.version == slotVersion &&
        header.kdfId == SlotHeader.KDF_ARGON2ID &&
        header.factorId == SlotHeader.FACTOR_OS_STORE_HMAC &&
        header.memoryKiB in MIN_MEMORY_KIB..MAX_MEMORY_KIB &&
        header.passes in MIN_PASSES..MAX_PASSES &&
        header.lanes == 1

    override fun suggest(): CharArray? = null

    fun resetCounts() {
        stretchCalls = 0
        factorInputCalls = 0
    }

    companion object {
        const val MIN_MEMORY_KIB = 65_536L
        const val MAX_MEMORY_KIB = 1_048_576L
        const val MIN_PASSES = 3
        const val MAX_PASSES = 10
        const val MIN_SET_LENGTH = 12
        const val MAX_UNLOCK_LENGTH = 256
    }
}
