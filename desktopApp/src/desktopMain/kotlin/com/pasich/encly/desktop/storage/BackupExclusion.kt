package com.pasich.encly.desktop.storage

import java.nio.file.Path

/**
 * Keeps the data directory out of the operating system's backups, as Android (`allowBackup=false`) and iOS
 * (`isExcludedFromBackup`) do: the vault, its key slots and the PIN's device secret belong on this device only.
 * Where the OS has no backup of the user's home to keep it out of (Windows: the data is in the local profile,
 * which File History and OneDrive folder backup do not include; Linux: the XDG data directory is the user's own
 * choice to back up) nothing is done.
 */
fun interface BackupExclusion {
    /** Excludes [directory] (and everything in it); false if it could not be done. Safe to repeat. */
    fun exclude(directory: Path): Boolean
}

/**
 * The extended attribute Time Machine reads to leave a path out of backups for good (the same one `tmutil
 * addexclusion -p` writes): `com.apple.metadata:com_apple_backup_excludeItem`, holding the binary property list of
 * the string `com.apple.backupd`.
 */
object TimeMachineExclusion {
    const val ATTRIBUTE = "com.apple.metadata:com_apple_backup_excludeItem"
    const val OWNER = "com.apple.backupd"

    // Markers of the binary property list format: an ASCII string whose length follows as an int object, and
    // a 1-byte int.
    private const val ASCII_STRING_MARKER: Byte = 0x5F
    private const val ONE_BYTE_INT_MARKER: Byte = 0x10
    private const val TRAILER_UNUSED = 6

    /** The value of [ATTRIBUTE]: a `bplist00` holding the single ASCII string [OWNER]. */
    fun attributeValue(): ByteArray {
        val text = OWNER.toByteArray(Charsets.US_ASCII)
        // Object 0: marker 0x5F (ASCII string, length in the next int object), 0x10 (1-byte int), the length, the text.
        val objectBytes = byteArrayOf(ASCII_STRING_MARKER, ONE_BYTE_INT_MARKER, text.size.toByte()) + text
        val header = "bplist00".toByteArray(Charsets.US_ASCII)
        val offsetTableOffset = header.size + objectBytes.size
        val offsetTable = byteArrayOf(header.size.toByte())
        // Trailer: 6 unused bytes, offset size 1, object reference size 1, 1 object, top object 0, table offset.
        val trailer =
            ByteArray(TRAILER_UNUSED) + byteArrayOf(1, 1) + long(1) + long(0) + long(offsetTableOffset.toLong())
        return header + objectBytes + offsetTable + trailer
    }

    private fun long(value: Long): ByteArray = ByteArray(Long.SIZE_BYTES) { index ->
        (value shr (Long.SIZE_BITS - Byte.SIZE_BITS * (index + 1))).toByte()
    }
}
