package com.pasich.encly.data.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.atomicfu.atomic

/**
 * The vault driver's wrapper that keeps every connection of Room's pool small.
 *
 * SQLite gives each connection its own page cache (2 MiB by default), and Room's pool in WAL mode
 * opens up to four readers beside the writer. A notes vault reads a few kilobytes per screen, so
 * each connection gets [CACHE_KIB] instead. The cache is a per-connection, in-memory setting: the
 * file, and so the cipher settings that define its format (page size, KDF, HMAC), are not touched.
 *
 * It also counts the connections the pool opened ([opened]), which is how the tests check the pool
 * stays at the size [SecureDatabaseManager] caps it to.
 */
internal class LeanDriver(private val delegate: SQLiteDriver) : SQLiteDriver {
    private val count = atomic(0)

    /** How many connections were opened through this driver so far (closed ones included). */
    val opened: Int get() = count.value

    override fun open(fileName: String): SQLiteConnection {
        val connection = delegate.open(fileName)
        var ready = false
        try {
            connection.execSQL("PRAGMA cache_size = -$CACHE_KIB")
            ready = true
        } finally {
            if (!ready) connection.close()
        }
        count.incrementAndGet()
        return connection
    }

    internal companion object {
        /** A connection's page cache, in KiB (a negative `cache_size` is a size, not a page count). */
        const val CACHE_KIB = 1024
    }
}
