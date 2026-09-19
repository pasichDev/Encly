package com.pasich.encly.data.database

import android.content.Context
import androidx.room.Room
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
class SecureDatabaseManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private var database: AppDatabase? = null
    private var isUnlocked = false

    fun isDatabaseUnlocked(): Boolean = isUnlocked

    fun hasEncryptedDatabase(): Boolean =
        SQLCipherUtils.getDatabaseState(context, DB_NAME) == SQLCipherUtils.State.ENCRYPTED

    fun getDatabase(): AppDatabase =
        database?.takeIf { isUnlocked }
            ?: error("Database accessed before unlock — call unlockDatabase() first")

    @Suppress("ReturnCount") // Fail-closed vault-state/key gates are clearer as early exits.
    @Synchronized
    fun unlockDatabase(
        secretKey: SecretKey,
        allowCreate: Boolean = false
    ): Boolean {
        if (isUnlocked) return true

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

            val openedDatabase = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(SupportOpenHelperFactory(passphraseForRoom))
                .fallbackToDestructiveMigration(false)
                .build()

            // Force SQLCipher/Room initialization before publishing the database as unlocked.
            // This catches key/open/schema failures here instead of on the first repository call.
            openedDatabase.openHelper.writableDatabase

            database = openedDatabase
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
            SensitiveDataCleaner.clear(passphraseForRoom)
        }
    }

    private fun canOpenDatabase(passphrase: ByteArray): Boolean =
        try {
            val factory = SupportOpenHelperFactory(passphrase)
            val db = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            ).openHelperFactory(factory).build()

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
    }

    fun wipe() {
        reset()
        deleteDatabaseFiles()
    }

    companion object {
        private const val TAG = "SecureDatabaseManager"
        private const val DB_NAME = "database.db"

        init {
            System.loadLibrary("sqlcipher")
            // SQLCipher's Java client logs to Logcat by default. Encly's vault layer
            // keeps third-party database diagnostics out of system-visible logs too.
            Logger.setTarget(NoopTarget())
        }
    }
}
