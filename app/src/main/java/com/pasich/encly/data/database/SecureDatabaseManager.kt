package com.pasich.encly.data.database

import android.content.Context
import com.pasich.encly.core.AppLogger
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.pasich.encly.core.security.cipher.SQLCipherUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import javax.crypto.SecretKey
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

    @Suppress("ReturnCount") // Fail-closed vault-state/key gates are clearer as early exits.
    @Synchronized
    fun unlockDatabase(
        secretKey: SecretKey,
        allowCreate: Boolean = false,
    ): Boolean {
        if (isUnlocked) return true
        ensureNativeLoaded()

        val rawPassphrase = secretKey.encoded ?: return false
        val passphraseForCheck = rawPassphrase.copyOf()
        val passphraseForRoom = rawPassphrase.copyOf()

        return try {
            when (SQLCipherUtils.getDatabaseState(context, DB_NAME)) {
                SQLCipherUtils.State.DOES_NOT_EXIST -> {
                    if (!allowCreate) return false
                }

                SQLCipherUtils.State.UNENCRYPTED -> {
                    // Never silently adopt/migrate a plaintext database into a committed vault.
                    return false
                }

                SQLCipherUtils.State.ENCRYPTED -> {
                    if (!canOpenDatabase(passphraseForCheck)) return false
                }
            }

            // No destructive fallback: a schema change without a migration must fail to open
            // (and be caught in tests), never drop the user's notes.
            val openedDatabase = VaultSchema.databaseBuilder(context.applicationContext, DB_NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphraseForRoom))
                .build()

            // Force SQLCipher/Room initialization before publishing the database as unlocked.
            // This catches key/open/schema failures here instead of on the first repository call.
            openedDatabase.openHelper.writableDatabase

            database = openedDatabase
            roomPassphrase = passphraseForRoom
            isUnlocked = true
            true
        } catch (e: Exception) {
            database?.close()
            database = null
            isUnlocked = false
            AppLogger.e(TAG, "Failed to unlock the database", e)
            false
        } finally {
            SensitiveDataCleaner.clear(rawPassphrase)
            SensitiveDataCleaner.clear(passphraseForCheck)
            // Only a failed open may wipe the Room buffer here; on success it is owned by the
            // live factory until reset().
            if (roomPassphrase !== passphraseForRoom) SensitiveDataCleaner.clear(passphraseForRoom)
        }
    }

    private fun canOpenDatabase(passphrase: ByteArray): Boolean = try {
        val factory = SupportOpenHelperFactory(passphrase)
        // Opening runs any pending upgrade, so the key probe needs the migrations too;
        // without them a schema bump would look like a wrong key.
        val db = VaultSchema.databaseBuilder(context.applicationContext, DB_NAME)
            .openHelperFactory(factory)
            .build()

        try {
            db.openHelper.readableDatabase
            true
        } finally {
            db.close()
        }
    } catch (_: Exception) {
        false
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
