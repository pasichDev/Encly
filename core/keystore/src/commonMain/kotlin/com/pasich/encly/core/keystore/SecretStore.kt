package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.KeyStoreProblem
import com.pasich.encly.core.security.PinKeySlot

/**
 * The operating system's secret store, as far as the desktop PIN factor needs it ([DesktopPinFactor]): one 32-byte
 * secret per [PinKeySlot], which only the signed-in OS user can read back. (iOS does not keep a secret at all, see
 * IosPinFactor.)
 * Implementations never write the secret anywhere unprotected, never log it, and wipe every copy they hold once they
 * have handed it over.
 */
internal interface SecretStore {
    /** The secret of [slot], or null when there is none. The caller wipes the result. */
    @Throws(SecretStoreException::class)
    fun read(slot: PinKeySlot): ByteArray?

    /**
     * Generates [SECRET_SIZE] random bytes from the platform CSPRNG, stores them as the secret of [slot] (replacing
     * any earlier one) and returns them. The caller wipes the result.
     */
    @Throws(SecretStoreException::class)
    fun create(slot: PinKeySlot): ByteArray

    /** Deletes the secret of [slot]; deleting one that is not there is not an error. */
    @Throws(SecretStoreException::class)
    fun delete(slot: PinKeySlot)

    /**
     * Whether [read] returning null proves the secret is gone for good. True where the store is the one place the
     * secret can be (a Windows file, the macOS login Keychain); false where "not found" may only mean that this is not
     * the store that holds it (the freedesktop Secret Service: another provider owns the bus, a closed database, a data
     * folder reached by another path).
     */
    val missingMeansLost: Boolean get() = true

    /**
     * Gets the store ready for [create] and [read]: running, unlocked, with a place to keep the secret, asking the user
     * through the system's own prompt where it has one. Creates no secret. Nothing to do by default.
     */
    @Throws(SecretStoreException::class)
    fun prepare() = Unit

    /** Ends a wait on the user that [prepare], [read] or [create] is in (a system prompt). Any thread. */
    fun abandonPending() = Unit

    companion object {
        const val SECRET_SIZE = 32
    }
}

/** What an entry is called in the store, per slot; slot A is the one every vault starts with. */
internal fun PinKeySlot.entryName(): String = when (this) {
    PinKeySlot.A -> ENTRY_NAME_A
    PinKeySlot.B -> ENTRY_NAME_B
}

internal const val ENTRY_NAME_A = "pin-factor-v1"
internal const val ENTRY_NAME_B = "pin-factor-v1-b"
internal const val APPLICATION_ID = "com.pasich.encly"

/**
 * A secret store failed. [lost] means the stored secret exists but can never be read again (for example a
 * DPAPI blob that no longer decrypts); a missing entry is not an error (read returns null) and a store that
 * is merely unavailable or locked is not lost.
 */
internal class SecretStoreException(
    val lost: Boolean,
    message: String,
    cause: Throwable? = null,
    /** What an unavailable store is missing, for the message; null where that is not known. */
    val problem: KeyStoreProblem? = null,
) : Exception(message, cause)
