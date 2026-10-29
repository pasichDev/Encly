package com.pasich.encly.core.security

/**
 * The file operations [VaultStore] needs, kept behind an interface because they carry its
 * durability promise: kotlinx-io has neither an fsync nor an atomic replace, so the
 * implementation (JvmVaultFileIo on Android and the JVM) does the write-temp, fsync, atomic-rename
 * itself. Every failure is a [kotlinx.io.IOException].
 */
public interface VaultFileIo {
    /** Whether the vault file exists. */
    public fun exists(): Boolean

    /** The whole vault file. */
    public fun read(): ByteArray

    /** Removes the temp file a write that never reached its rename left behind; never throws. */
    public fun deleteTemp()

    /**
     * Replaces the vault file with [bytes] so that a crash leaves the old content or the new,
     * never a mix: the bytes go to a temp file, which is fsynced, then [beforeRename] runs (a test
     * hook), then the temp file is renamed over the vault file atomically. A failure removes the
     * temp file and throws.
     */
    public fun replace(bytes: ByteArray, beforeRename: (() -> Unit)?)

    /** Deletes the vault file; true when it is gone afterwards (it may never have existed). */
    public fun delete(): Boolean
}
