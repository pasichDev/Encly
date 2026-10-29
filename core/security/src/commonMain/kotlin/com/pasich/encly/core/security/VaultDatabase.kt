package com.pasich.encly.core.security

/**
 * The two vaults a device can hold: the real one, and the decoy vault the duress PIN opens (its own
 * database, its own random DEK). At most one of them is open at a time.
 */
public enum class VaultKind {
    PRIMARY,
    DECOY,
}

/**
 * What [SecurityManager] needs from the encrypted database: open it with a DEK, ask whether it is open,
 * close it, delete it. The database module implements it; the security logic never sees Room or the
 * cipher. A [VaultKind] names the database file, `PRIMARY` unless said otherwise.
 */
public interface VaultDatabase {
    /** Whether a database is open. */
    public fun isDatabaseUnlocked(): Boolean

    /** Whether [vault]'s database file exists and is encrypted. */
    public fun hasEncryptedDatabase(vault: VaultKind = VaultKind.PRIMARY): Boolean

    /** [unlockDatabase] for the real vault's database. */
    public fun unlockDatabase(dek: ByteArray, allowCreate: Boolean = false): Boolean

    /**
     * Opens [vault]'s database with the raw 32-byte [dek]; the caller keeps (and wipes) [dek]. A missing
     * file is created only when [allowCreate]. While one is open, a repeated call succeeds only with the
     * same key and vault.
     */
    public fun unlockDatabase(dek: ByteArray, allowCreate: Boolean, vault: VaultKind): Boolean

    /** Closes the open database and wipes the copy of its key. */
    public fun reset()

    /** Closes the open database and deletes the database files. */
    public fun wipe()

    /**
     * Deletes the database file of [only] (every one when null) unless it is the open one; true when
     * what was asked for is gone afterwards.
     */
    public fun wipeIfClosed(only: VaultKind? = null): Boolean
}
