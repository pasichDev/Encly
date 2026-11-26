package com.pasich.encly.core.security

/**
 * The two places the device-bound PIN key can live. [A] is the one every vault starts with
 * (and the only one older builds know); a wipe-PIN unlock moves the PIN slot to the other one
 * and deletes the old key, so the erased slots can never be opened again (crypto-erase). The
 * vault records which one is active.
 */
public enum class PinKeySlot {
    A,
    B,
    ;

    public val other: PinKeySlot get() = if (this == A) B else A
}

/**
 * The device-bound half of the PIN key: an HMAC-SHA256 whose key never leaves this device's
 * secure hardware. Mixed into the PIN KEK, it makes every PIN guess run here, on the
 * Keystore, instead of on an attacker's GPU against a copied slot. An interface so JVM tests
 * can replace the Keystore.
 */
public interface PinHardwareFactor {
    /**
     * Creates the key in [slot] unless it already exists. True when it had to be created: any
     * slot sealed with a key that was there before (a key the system or a backup restore deleted)
     * can no longer be opened.
     */
    @Throws(PinFactorException::class)
    public fun ensureKey(slot: PinKeySlot): Boolean

    /** Replaces the key in [slot] with a new one; slots sealed with the old one can no longer be opened. */
    @Throws(PinFactorException::class)
    public fun reset(slot: PinKeySlot)

    /** HMAC-SHA256 of [data] under the key in [slot]. The caller wipes the result. */
    @Throws(PinFactorException::class)
    public fun mac(slot: PinKeySlot, data: ByteArray): ByteArray

    /** Deletes the key in [slot], if any. Never throws. */
    public fun delete(slot: PinKeySlot)

    /**
     * Whether the store of the keys is not known to be gone for good when a key is not found in it: a desktop keyring
     * can be another one than the one the key was made in (another provider is running, a keyring that is closed, a
     * moved data folder), so "not found" there is a state to retry and not the end ([PinFactorException.notFound]).
     * False on Android and iOS, where a missing key is a lost key.
     */
    public val missingKeyMayReturn: Boolean get() = false

    /**
     * Makes the store ready for [ensureKey] before anything depends on it: starts, unlocks or creates what it needs,
     * asking the user through the system's own prompt where there is one. Nothing is created in it. Fails with a
     * [PinFactorException] whose [PinFactorException.problem] says what is missing. Nothing to do by default (Android
     * and iOS keep their keys in hardware that is always there).
     */
    @Throws(PinFactorException::class)
    public fun prepare(): Unit = Unit

    /**
     * Ends what [prepare], [ensureKey], [reset] or [mac] is waiting for on the user (a system prompt): the wait fails
     * at once with [KeyStoreProblem.DISMISSED]. Called when the vault is locked, so a prompt left open does not outlive
     * the lock. Safe to call from any thread, at any time; nothing by default.
     */
    public fun abandonPending(): Unit = Unit
}

/** What a key store that is not usable right now is missing; see [PinFactorException.problem]. */
public enum class KeyStoreProblem {
    /** No store is running at all (no Secret Service on the session bus). */
    NOT_RUNNING,

    /** The store is there but locked, and could not be unlocked. */
    LOCKED,

    /** The store has no default place to keep the key in, and one could not be made. */
    NO_DEFAULT,

    /** The user dismissed the system prompt, or it was left open until it timed out or the vault was locked. */
    DISMISSED,

    /** The store failed for another reason. */
    OTHER,
}

/**
 * The factor failed. [lost] means the key is gone for good (deleted, or invalidated by the
 * system): the PIN slot can never be opened again on this device and only the recovery phrase
 * (or the biometric slot) still unlocks. Otherwise the failure may be transient.
 *
 * [unavailable] (only with `lost = false`) means the store that holds the key is not usable right now but is
 * expected to come back once the user acts, e.g. a locked desktop keyring; [problem] says what is missing. The attempt
 * that met it is not a guess, and the screen can say what to do. Android never sets it.
 *
 * [notFound] (only with `lost = false`) means the store answered but holds no key, on a platform where that does not
 * prove the key is gone ([PinHardwareFactor.missingKeyMayReturn]): the vault is intact, and trying again, the recovery
 * phrase or, last of all, a wipe are the user's choices. Android never sets it.
 */
public class PinFactorException(
    public val lost: Boolean,
    cause: Throwable? = null,
    public val unavailable: Boolean = false,
    public val notFound: Boolean = false,
    public val problem: KeyStoreProblem? = null,
) : Exception(cause)
