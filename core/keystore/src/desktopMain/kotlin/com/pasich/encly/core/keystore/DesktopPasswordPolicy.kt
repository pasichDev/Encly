package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.SecretKind
import com.pasich.encly.core.security.SecretRule
import com.pasich.encly.core.security.SecretStretchException
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.pasich.encly.core.security.SlotHeader
import com.pasich.encly.core.security.UnlockSecretPolicy

/**
 * The desktop vault secret: a password, slot version 4.
 *
 * ```
 * pw   = UTF-8(NFC(password))                       unpaired surrogates rejected
 * sw   = Argon2id(pw, salt, m, t, p, 32)            version 1.3, the cost read from the slot
 * hw   = HMAC-SHA256(deviceSecret, "encly/secret/hw/v4" ‖ salt ‖ pw)
 * ikm  = sw ‖ hw
 * kek  = HKDF-SHA256(ikm, salt, "encly/secret/kek/v4")     likewise ".../wipe/v4" and ".../decoy/v4"
 * ```
 *
 * New slots are written with m = 65 536 KiB, t = 3, p = 1. A slot is tried only when [accepts] says its header is in
 * range, before anything is allocated, so a damaged or hostile slot can never make the app reserve gigabytes.
 *
 * Where the NFC form lives: [canonical] makes it once, and [mayBeSet], [stretch] and [factorInput] take that form and
 * only count or UTF-8-encode it (the same encoder for the stretch and for the device factor, so the two cannot
 * disagree, and nothing is normalised twice). Every byte array made here from the password is wiped before the call
 * returns. What cannot be wiped is the `String` the JDK normalizer works on for a password with a character from U+0300
 * up (see [PasswordCanonical]).
 */
public class DesktopPasswordPolicy internal constructor(private val engine: Argon2Function) : UnlockSecretPolicy {
    /** The policy with the real Argon2id. */
    public constructor() : this(Argon2id.production)

    override val kind: SecretKind = SecretKind.PASSWORD

    override val slotVersion: Byte = SlotHeader.PASSWORD_VERSION

    override val chargesForUnusableSlot: Boolean = false

    override val maxTypedUnits: Int = PasswordCanonical.TYPED_MAX_UNITS

    private val hardwarePrefix = "encly/secret/hw/v4".encodeToByteArray()
    private val kekBytes = "encly/secret/kek/v4".encodeToByteArray()
    private val wipeBytes = "encly/secret/wipe/v4".encodeToByteArray()
    private val decoyBytes = "encly/secret/decoy/v4".encodeToByteArray()

    override val kekInfo: ByteArray get() = kekBytes.copyOf()

    override val wipeInfo: ByteArray get() = wipeBytes.copyOf()

    override val decoyInfo: ByteArray get() = decoyBytes.copyOf()

    /**
     * The NFC form of [typed], or null. [typed] is first held to [maxTypedUnits] (a cap on normalisation work only),
     * then normalised, and the unlock bound (1 to 256 units) applies to the NFC form: any typing, decomposed or not,
     * of a password that [mayBeSet] accepts gets a canonical form.
     */
    override fun canonical(typed: CharArray): CharArray? {
        val nfc = canonicalForSetting(typed)
        if (nfc == null || PasswordCanonical.withinUnlockBounds(nfc)) return nfc
        SensitiveDataCleaner.clear(nfc)
        return null
    }

    /**
     * The NFC form of [typed] within the typed cap, with no unlock bound: a new password whose NFC form is over 256
     * units (but within the cap) is then told it is [SecretRule.TOO_LONG] by [mayBeSet], not that it "cannot be
     * used". Unlocking never takes this form ([canonical] applies the bound), so what unlock accepts is unchanged.
     */
    override fun canonicalForSetting(typed: CharArray): CharArray? =
        if (PasswordCanonical.withinTypedCap(typed)) PasswordCanonical.canonical(typed) else null

    override fun mayBeSet(canonical: CharArray): SecretRule = PasswordCanonical.checkForSet(canonical)

    override fun newHeader(salt: ByteArray): SlotHeader = SlotHeader(
        version = SlotHeader.PASSWORD_VERSION,
        kdfId = SlotHeader.KDF_ARGON2ID,
        factorId = SlotHeader.FACTOR_OS_STORE_HMAC,
        memoryKiB = Argon2id.MIN_MEMORY_KIB.toLong(),
        passes = Argon2id.MIN_PASSES,
        lanes = Argon2id.LANES,
        salt = salt,
    )

    @Throws(SecretStretchException::class)
    override fun stretch(canonical: CharArray, header: SlotHeader): ByteArray {
        // Checked in the Long domain: a memory of 2^32 + 65 536 would be 65 536 after toInt().
        require(accepts(header)) { "A slot header this policy does not accept" }
        val password = PasswordCanonical.utf8(canonical)
        try {
            return Argon2id.stretch(
                password = password,
                salt = header.salt,
                memoryKib = header.memoryKiB.toInt(),
                passes = header.passes,
                lanes = header.lanes,
                engine = engine,
            )
        } finally {
            SensitiveDataCleaner.clear(password)
        }
    }

    override fun factorInput(canonical: CharArray, salt: ByteArray): ByteArray {
        val password = PasswordCanonical.utf8(canonical)
        try {
            // One exact-size array: a chain of `+` would leave a copy of the password bytes behind at every step.
            val input = ByteArray(hardwarePrefix.size + salt.size + password.size)
            hardwarePrefix.copyInto(input)
            salt.copyInto(input, hardwarePrefix.size)
            password.copyInto(input, hardwarePrefix.size + salt.size)
            return input
        } finally {
            SensitiveDataCleaner.clear(password)
        }
    }

    override fun accepts(header: SlotHeader): Boolean = header.version == SlotHeader.PASSWORD_VERSION &&
        header.kdfId == SlotHeader.KDF_ARGON2ID &&
        header.factorId == SlotHeader.FACTOR_OS_STORE_HMAC &&
        header.memoryKiB in Argon2id.MIN_MEMORY_KIB.toLong()..Argon2id.MAX_MEMORY_KIB.toLong() &&
        header.passes in Argon2id.MIN_PASSES..Argon2id.MAX_PASSES &&
        header.lanes == Argon2id.LANES &&
        header.salt.size == Argon2id.SALT_BYTES

    /** [PassphraseGenerator.DEFAULT_WORD_COUNT] BIP-39 words, which [mayBeSet] accepts. */
    override fun suggest(): CharArray = PassphraseGenerator.generate()
}
