package com.pasich.encly.data.database

import android.content.Context
import com.pasich.encly.core.AppLogger
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.pasich.encly.core.security.cipher.SQLCipherUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import net.zetetic.database.sqlcipher.SQLiteConnection
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the SQLCipher-encrypted Room database and its unlocked lifecycle.
 *
 * A committed vault must already have an encrypted database file. Database creation is allowed
 * only during the atomic first-run setup path.
 */
@Singleton
class SecureDatabaseManager @Inject constructor(@param:ApplicationContext private val context: Context) :
    DatabaseProvider {
    private var database: AppDatabase? = null
    private var isUnlocked = false

    /**
     * The passphrase buffer handed to Room's [SupportOpenHelperFactory]. SQLCipher keeps this
     * exact array (no copy) in its open-helper configuration and re-keys every new pool
     * connection from it (WAL readers, reopen after a pool reset), so it must stay intact for
     * as long as [database] is open and is zeroed only after [reset] closes it.
     */
    private var roomPassphrase: ByteArray? = null

    fun isDatabaseUnlocked(): Boolean = isUnlocked

    fun hasEncryptedDatabase(): Boolean =
        SQLCipherUtils.getDatabaseState(context, DB_NAME) == SQLCipherUtils.State.ENCRYPTED

    override fun getDatabase(): AppDatabase = database?.takeIf { isUnlocked }
        ?: error("Database accessed before unlock — call unlockDatabase() first")

    /**
     * Opens the vault with the raw 32-byte [dek]; the caller keeps (and wipes) [dek].
     *
     * The key reaches SQLCipher in its raw-key form (`x'<64 hex>'`), so SQLCipher uses it as
     * the page key directly instead of running its own PBKDF2 over it on every connection: the
     * DEK is already a uniformly random 256-bit key.
     *
     * While open, a second call succeeds only with the same key (compared in constant time);
     * any other key is refused, never silently accepted.
     */
    @Suppress("ReturnCount") // Fail-closed vault-state/key gates are clearer as early exits.
    @Synchronized
    fun unlockDatabase(dek: ByteArray, allowCreate: Boolean = false): Boolean {
        if (dek.size != DEK_LENGTH) return false
        val passphrase = rawKeyPassphrase(dek)
        if (isUnlocked) {
            return try {
                roomPassphrase?.let { MessageDigest.isEqual(it, passphrase) } == true
            } finally {
                SensitiveDataCleaner.clear(passphrase)
            }
        }
        ensureNativeLoaded()

        var openedDatabase: AppDatabase? = null
        return try {
            when (SQLCipherUtils.getDatabaseState(context, DB_NAME)) {
                SQLCipherUtils.State.DOES_NOT_EXIST -> if (!allowCreate) return false

                // Never silently adopt/migrate a plaintext database into a committed vault.
                SQLCipherUtils.State.UNENCRYPTED -> return false

                SQLCipherUtils.State.ENCRYPTED -> Unit
            }

            // No destructive fallback: a schema change without a migration must fail to open
            // (and be caught in tests), never drop the user's notes.
            val opened = VaultSchema.databaseBuilder(context.applicationContext, DB_NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase, VaultConnectionHook, false))
                .build()
            openedDatabase = opened

            // One open, which is also the key check: a wrong key, a damaged file or a failed
            // migration throws here, before the database is published as unlocked.
            opened.openHelper.writableDatabase

            database = opened
            roomPassphrase = passphrase
            isUnlocked = true
            true
        } catch (e: Exception) {
            openedDatabase?.close()
            database = null
            isUnlocked = false
            AppLogger.e(TAG, "Failed to unlock the database", e)
            false
        } finally {
            // Only a failed open may wipe the buffer here; on success it is owned by the
            // live factory until reset().
            if (roomPassphrase !== passphrase) SensitiveDataCleaner.clear(passphrase)
        }
    }

    private fun deleteDatabaseFiles() {
        try {
            val dbFile = context.getDatabasePath(DB_NAME)
            File(dbFile.absolutePath).delete()
            File(dbFile.absolutePath + "-wal").delete()
            File(dbFile.absolutePath + "-shm").delete()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to delete database files", e)
        }
    }

    @Synchronized
    fun reset() {
        database?.close()
        database = null
        isUnlocked = false
        roomPassphrase?.let(SensitiveDataCleaner::clear)
        roomPassphrase = null
    }

    fun wipe() {
        reset()
        deleteDatabaseFiles()
    }

    companion object {
        private const val TAG = "SecureDatabaseManager"
        private const val DB_NAME = "database.db"
        private const val DEK_LENGTH = 32
        private val HEX = "0123456789abcdef".toCharArray()

        /**
         * `x'<hex>'` as ASCII bytes: SQLCipher's raw-key syntax. The caller wipes the result.
         * Built byte by byte so no String copy of the key exists.
         */
        internal fun rawKeyPassphrase(dek: ByteArray): ByteArray {
            val out = ByteArray(dek.size * 2 + RAW_KEY_OVERHEAD)
            out[0] = 'x'.code.toByte()
            out[1] = '\''.code.toByte()
            dek.forEachIndexed { index, byte ->
                val value = byte.toInt() and BYTE_MASK
                out[RAW_KEY_PREFIX + index * 2] = HEX[value ushr NIBBLE_BITS].code.toByte()
                out[RAW_KEY_PREFIX + index * 2 + 1] = HEX[value and NIBBLE_MASK].code.toByte()
            }
            out[out.size - 1] = '\''.code.toByte()
            return out
        }

        private const val RAW_KEY_OVERHEAD = 3
        private const val RAW_KEY_PREFIX = 2
        private const val BYTE_MASK = 0xFF
        private const val NIBBLE_BITS = 4
        private const val NIBBLE_MASK = 0x0F

        // Loaded on first use rather than in a static initializer, so the class can be
        // constructed (and mocked in JVM tests) without the native library.
        private val nativeLoaded by lazy {
            System.loadLibrary("sqlcipher")
            // SQLCipher's Java client logs to Logcat by default. Encly's vault layer
            // keeps third-party database diagnostics out of system-visible logs too.
            Logger.setTarget(NoopTarget())
            true
        }

        private fun ensureNativeLoaded() {
            check(nativeLoaded)
        }
    }
}

/**
 * Per-connection SQLCipher settings, both best effort (a build without them must still open
 * the vault):
 * - before keying, native logging off (`cipher_log_level = NONE`): SQLCipher's own log lines
 *   can name the database and describe key or page errors, and must not reach logcat. The Java
 *   logger is already a no-op (see [SecureDatabaseManager]).
 * - after keying, memory security on (`cipher_memory_security`): freed memory is zeroed.
 */
@Suppress("TooGenericExceptionCaught") // Best effort: never fail the open over a pragma.
private object VaultConnectionHook : SQLiteDatabaseHook {
    override fun preKey(connection: SQLiteConnection) {
        pragma(connection, "PRAGMA cipher_log_level = NONE")
    }

    override fun postKey(connection: SQLiteConnection) {
        pragma(connection, "PRAGMA cipher_memory_security = ON")
    }

    /** Some pragmas answer with a row, some with none; either is fine. */
    private fun pragma(connection: SQLiteConnection, sql: String) {
        try {
            connection.executeForString(sql, null, null)
        } catch (_: Exception) {
            try {
                connection.execute(sql, null, null)
            } catch (e: Exception) {
                AppLogger.w("SecureDatabaseManager", "SQLCipher pragma not applied", e)
            }
        }
    }
}
