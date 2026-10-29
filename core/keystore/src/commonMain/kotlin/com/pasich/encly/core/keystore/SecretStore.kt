package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.PinKeySlot

/**
 * The operating system's secret store, as far as the PIN factors need it ([DesktopPinFactor] on the desktop, the iOS
 * factor on iOS): one 32-byte secret per [PinKeySlot], which only the signed-in OS user can read back.
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
internal class SecretStoreException(val lost: Boolean, message: String, cause: Throwable? = null) :
    Exception(message, cause)
