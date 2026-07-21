package com.pasich.encly.data.database

import android.content.Context
import androidx.room.Room
import com.pasich.encly.core.AppLogger
import com.pasich.encly.core.security.cipher.SQLCipherUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the SQLCipher-encrypted Room database and its unlocked lifecycle.
 * The database is opened with a seed-derived passphrase; until unlocked, callers
 * must not persist real data through it.
 */
@Singleton
class SecureDatabaseManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private var database: AppDatabase? = null
    private var isUnlocked = false

    /**
     * Returns the unlocked encrypted database. Fails loudly if accessed before
     * unlock: silently handing back a throwaway in-memory database would route real
     * writes into volatile, unencrypted storage and lose them. Callers must ensure
     * the DB is unlocked (post-auth) before touching any DAO.
     */
    fun getDatabase(): AppDatabase {
        return database?.takeIf { isUnlocked }
            ?: throw IllegalStateException("Database accessed before unlock — call unlockDatabase() first")
    }


    @Synchronized
    fun unlockDatabase(secretKey: SecretKey): Boolean {
        if (isUnlocked) {
            AppLogger.d(TAG, "Database already unlocked")
            return true
        }


        return try {
            val rawPassphrase = secretKey.encoded
            val passphraseForCheck = rawPassphrase.copyOf()
            val passphraseForRoom = rawPassphrase.copyOf()
            AppLogger.d(TAG, "Key received. Length: ${rawPassphrase.size} bytes")

            val dbFile = context.getDatabasePath(DB_NAME)
            AppLogger.d(TAG, "Database path: ${dbFile.absolutePath}")

            val state = SQLCipherUtils.getDatabaseState(context, DB_NAME)
            AppLogger.d(TAG, "Database state before unlock: $state")



            if (state == SQLCipherUtils.State.ENCRYPTED) {
                AppLogger.d(TAG, "Database exists and is encrypted. Verifying it opens")
                if (!canOpenDatabase(passphraseForCheck)) {
                    AppLogger.e(TAG, "Cannot open the encrypted database with this key")
                    return false
                }
            }

            if (state == SQLCipherUtils.State.DOES_NOT_EXIST) {
                AppLogger.d(TAG, "Database does not exist. Creating a new encrypted database")
            }

            AppLogger.d(TAG, "Initializing Room with encryption")
            database = Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, DB_NAME
            ).openHelperFactory(SupportOpenHelperFactory(passphraseForRoom))
                .fallbackToDestructiveMigration(false).build()

            isUnlocked = true
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to unlock the database", e)
            false
        }
    }

    private fun canOpenDatabase(passphrase: ByteArray): Boolean {
        return try {
            AppLogger.d(TAG, "Checking database open via SupportOpenHelperFactory")

            val factory = SupportOpenHelperFactory(passphrase)
            val db = Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, DB_NAME
            ).openHelperFactory(factory).build()

            // Trigger initialization
            db.openHelper.readableDatabase
            db.close()

            AppLogger.d(TAG, "Database opened successfully")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error opening the database", e)
            false
        }
    }

    private fun deleteDatabaseFiles() {
        try {
            AppLogger.d(TAG, "Deleting database files")
            val dbFile = context.getDatabasePath(DB_NAME)
            val wal = File(dbFile.absolutePath + "-wal")
            val shm = File(dbFile.absolutePath + "-shm")
            dbFile.delete()
            wal.delete()
            shm.delete()
            AppLogger.d(TAG, "Database files deleted successfully")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to delete database files", e)
        }
    }

    fun reset() {
        AppLogger.d(TAG, "Resetting database")
        database?.close()
        database = null
        isUnlocked = false
        AppLogger.d(TAG, "Database reset")
    }

    /** Full wipe: closes the database and deletes its files from disk. */
    fun wipe() {
        reset()
        deleteDatabaseFiles()
    }

    companion object {
        private const val TAG = "SecureDatabaseManager"
        private const val DB_NAME = "database.db"
    }
}
