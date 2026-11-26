package com.pasich.encly.core.security

/** What the user types to unlock the vault. */
public enum class SecretKind {
    /** A 6 to 12 digit PIN (Android, iOS). */
    PIN,

    /** A password (desktop, from slot version 4). */
    PASSWORD,
}

/** Whether a secret may be chosen as the vault's secret, the wipe secret or the duress secret. */
public enum class SecretRule {
    /** The secret may be set. */
    OK,

    /** The secret is too short to be set. */
    TOO_SHORT,

    /** The secret is long enough but too simple to be set. */
    TOO_SIMPLE,

    /** The secret is longer than a secret of this kind may be set. */
    TOO_LONG,

    /**
     * What was typed cannot be a secret of this kind at all (a PIN with a letter in it, a password with a lone
     * surrogate or beyond the length the lock screen takes). Only [AuthenticationManager.checkNewSecret] says it.
     */
    INVALID,
}

/**
 * The slow half of the keys could not be made on this attempt (a stretch that ran out of memory). It says nothing
 * about the secret: the caller does not count the attempt as a wrong guess, and the attempt is given back. Thrown by
 * [UnlockSecretPolicy.stretch], like [PinFactorException] by the device factor. It carries no input.
 */
public class SecretStretchException(cause: Throwable? = null) : Exception(cause)

/**
 * What a main slot says about how its keys are made: the slot [version], the ids of the stretching function and of
 * the device factor, the stretching cost, and the [salt]. Version 3 slots (the PIN) carry no parameters of their own:
 * their header is [PIN_VERSION] with [KDF_PBKDF2], [FACTOR_OS_STORE_HMAC] and zeros for the cost.
 *
 * A header read from a slot is attacker-controlled input: nothing is allocated or stretched for it before the bound
 * [UnlockSecretPolicy.accepts] has said yes. [salt] belongs to whoever holds the header, who wipes it.
 */
public class SlotHeader(
    /** The slot format version: [PIN_VERSION] or [PASSWORD_VERSION]. */
    public val version: Byte,
    /** The id of the stretching function, 0 to 255 ([KDF_PBKDF2], [KDF_ARGON2ID]). */
    public val kdfId: Int,
    /** The id of the device factor, 0 to 255 ([FACTOR_OS_STORE_HMAC]). */
    public val factorId: Int,
    /** The memory cost in KiB, 0 to 4 294 967 295 (an Argon2 parameter; 0 where the function has none). */
    public val memoryKiB: Long = 0L,
    /** The number of passes, 0 to 255 (an Argon2 parameter; 0 where the function has none). */
    public val passes: Int = 0,
    /** The number of lanes, 0 to 255 (an Argon2 parameter; 0 where the function has none). */
    public val lanes: Int = 0,
    /** The salt of the slot. */
    public val salt: ByteArray,
) {
    /** Whether [other] has the same version, ids and costs; the salt is not compared. */
    public fun sameFormatAs(other: SlotHeader): Boolean = version == other.version &&
        kdfId == other.kdfId &&
        factorId == other.factorId &&
        memoryKiB == other.memoryKiB &&
        passes == other.passes &&
        lanes == other.lanes

    /** A copy with a copy of the salt, so the two can be wiped on their own. */
    public fun copy(): SlotHeader = SlotHeader(version, kdfId, factorId, memoryKiB, passes, lanes, salt.copyOf())

    public companion object {
        /** The version of the PIN slots (Android, iOS). */
        public const val PIN_VERSION: Byte = 3

        /** The version of the password slots (desktop). */
        public const val PASSWORD_VERSION: Byte = 4

        /** PBKDF2-HMAC-SHA256: what the PIN slots use. Reserved in version 4, never written there. */
        public const val KDF_PBKDF2: Int = 1

        /** Argon2id, version 1.3. */
        public const val KDF_ARGON2ID: Int = 2

        /** HMAC-SHA256 under a key of the device's secret store. */
        public const val FACTOR_OS_STORE_HMAC: Int = 1
    }
}

/**
 * What the unlock secret is: how it is typed, what may be set, how it is stretched, what the device factor signs and
 * which slot format it lives in. [AuthenticationManager] runs every secret through this policy and knows nothing
 * else about PINs or passwords. A platform binds the one policy its users get ([PinSecretPolicy] today).
 *
 * The secrets handed in are the caller's to wipe; every array a method returns is the caller's to wipe too.
 */
public interface UnlockSecretPolicy {
    /** What the user types. */
    public val kind: SecretKind

    /** The version of the slots this policy writes. */
    public val slotVersion: Byte

    /** The HKDF info of the key that opens the main slot. */
    public val kekInfo: ByteArray

    /** The HKDF info of the key that opens the wipe slot. */
    public val wipeInfo: ByteArray

    /** The HKDF info of the key that opens the duress slot. */
    public val decoyInfo: ByteArray

    /**
     * The most UTF-16 units a typed secret of this kind may have before it is refused without being looked at
     * ([canonical] returns null for more): 12 for the PIN, 768 for the password (128 code points, the longest that may
     * be set, times 6, the most units one character takes decomposed, so any legal password fits however it is typed
     * or pasted). A field may use it as its cap. It is a limit on work, not the rule: [canonical] and [mayBeSet]
     * decide, and nothing here relies on the caller having cut anything.
     */
    public val maxTypedUnits: Int

    /**
     * A fresh copy of what was [typed] in the form the keys are made from, or null when it cannot be a secret of this
     * kind at all (a refused attempt). The caller wipes the copy.
     */
    public fun canonical(typed: CharArray): CharArray?

    /**
     * [canonical] for a secret that is being CHOSEN: the same form, but without the bound that only unlocking applies,
     * so that [mayBeSet] can say why a long one is refused ([SecretRule.TOO_LONG]) instead of the caller only learning
     * that it "cannot be used". What unlocking accepts is [canonical]'s and unchanged. The caller wipes the copy.
     */
    public fun canonicalForSetting(typed: CharArray): CharArray? = canonical(typed)

    /** Whether [canonical] may be chosen as a secret. */
    public fun mayBeSet(canonical: CharArray): SecretRule

    /** The header of a slot this policy writes now, around [salt] (which the header uses, not a copy of). */
    public fun newHeader(salt: ByteArray): SlotHeader

    /**
     * The slow, device-independent half of the keys. The caller wipes the result. [header] is one [accepts] said yes
     * to. Throws [SecretStretchException] when this attempt could not be made (not a wrong secret).
     */
    @Throws(SecretStretchException::class)
    public fun stretch(canonical: CharArray, header: SlotHeader): ByteArray

    /** What the device factor signs: the bytes the factor's MAC is taken over. The caller wipes the result. */
    public fun factorInput(canonical: CharArray, salt: ByteArray): ByteArray

    /**
     * Whether a slot with this [header] may be tried by this policy. Everything read from a slot is checked here
     * before anything is allocated or stretched for it; a slot that is not accepted does not exist for the attempt.
     */
    public fun accepts(header: SlotHeader): Boolean

    /**
     * Whether a typed attempt against a stored slot that [accepts] says no to (empty, unparsable, another version) is
     * counted as a wrong guess, with nothing derived. The PIN says yes, as it always has; a policy whose slot may
     * simply be one of an older format on this platform says no: such a slot is "no slot", nothing is counted,
     * derived or sent to the device factor.
     */
    public val chargesForUnusableSlot: Boolean

    /**
     * A fresh secret of this kind that [mayBeSet] accepts, for the user to take or change; null when this kind has
     * none to offer (a PIN). The caller wipes the result.
     */
    public fun suggest(): CharArray?
}
