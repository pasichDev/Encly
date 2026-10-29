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
}

/**
 * The factor failed. [lost] means the key is gone for good (deleted, or invalidated by the
 * system): the PIN slot can never be opened again on this device and only the recovery phrase
 * (or the biometric slot) still unlocks. Otherwise the failure may be transient.
 */
public class PinFactorException(public val lost: Boolean, cause: Throwable? = null) : Exception(cause)
