package com.pasich.encly.core.security

import com.pasich.encly.core.crypto.Crypto
import com.pasich.encly.core.security.PinSlots.DEK_LENGTH
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * The 6 to 12 digit PIN, as every vault before the desktop password has it: PBKDF2-HMAC-SHA256 over the digits with
 * 600 000 iterations, the device factor over `salt ‖ digits` (one byte per digit), the three `v3` HKDF infos, slot
 * version 3. This is the code that was in [AuthenticationManager], moved and not rewritten.
 */
@ContributesBinding(AppScope::class)
@Inject
public class PinSecretPolicy : UnlockSecretPolicy {
    override val kind: SecretKind = SecretKind.PIN

    override val slotVersion: Byte = SlotHeader.PIN_VERSION

    override val chargesForUnusableSlot: Boolean = true

    override val maxTypedUnits: Int = MAX_PIN_LENGTH

    private val kekBytes = "encly/pin/kek/v3".encodeToByteArray()
    private val wipeBytes = "encly/pin/wipe/v3".encodeToByteArray()
    private val decoyBytes = "encly/pin/decoy/v3".encodeToByteArray()

    override val kekInfo: ByteArray get() = kekBytes.copyOf()

    override val wipeInfo: ByteArray get() = wipeBytes.copyOf()

    override val decoyInfo: ByteArray get() = decoyBytes.copyOf()

    override fun canonical(typed: CharArray): CharArray? =
        if (typed.size in MIN_PIN_LENGTH..MAX_PIN_LENGTH && typed.all { it in '0'..'9' }) typed.copyOf() else null

    override fun mayBeSet(canonical: CharArray): SecretRule = SecretRule.OK

    override fun newHeader(salt: ByteArray): SlotHeader = SlotHeader(
        version = SlotHeader.PIN_VERSION,
        kdfId = SlotHeader.KDF_PBKDF2,
        factorId = SlotHeader.FACTOR_OS_STORE_HMAC,
        salt = salt,
    )

    @Throws(SecretStretchException::class)
    override fun stretch(canonical: CharArray, header: SlotHeader): ByteArray =
        Crypto.pbkdf2HmacSha256(canonical, header.salt, PBKDF2_ITERATIONS, DEK_LENGTH)

    override fun factorInput(canonical: CharArray, salt: ByteArray): ByteArray {
        val macInput = ByteArray(salt.size + canonical.size)
        salt.copyInto(macInput)
        canonical.forEachIndexed { index, c -> macInput[salt.size + index] = c.code.toByte() }
        return macInput
    }

    override fun accepts(header: SlotHeader): Boolean = header.version == SlotHeader.PIN_VERSION

    /** A PIN is chosen by the user: there is none to offer. */
    override fun suggest(): CharArray? = null

    private companion object {
        const val PBKDF2_ITERATIONS = 600_000
    }
}
