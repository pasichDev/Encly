package com.pasich.encly.core.security.cipher

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * Detects whether the app database file is missing, plaintext, or SQLCipher-encrypted.
 *
 * A plaintext SQLite database starts with the 16-byte magic header
 * "SQLite format 3\0" (ending in NUL); SQLCipher encrypts the header too, so an encrypted database
 * will not match. Inspecting the header keeps this check independent of any SQLCipher
 * runtime API.
 */
public object SQLCipherUtils {

    public enum class State { DOES_NOT_EXIST, UNENCRYPTED, ENCRYPTED }

    public fun getDatabaseState(dbPath: Path): State {
        if (!SystemFileSystem.exists(dbPath)) return State.DOES_NOT_EXIST
        return try {
            val header = ByteArray(SQLITE_MAGIC.size)
            val read = SystemFileSystem.source(dbPath).buffered().use { it.readAtMostTo(header) }
            if (read == SQLITE_MAGIC.size && header.contentEquals(SQLITE_MAGIC)) {
                State.UNENCRYPTED
            } else {
                State.ENCRYPTED
            }
        } catch (_: Exception) {
            State.ENCRYPTED
        }
    }

    private val SQLITE_MAGIC = "SQLite format 3\u0000".encodeToByteArray()
}
