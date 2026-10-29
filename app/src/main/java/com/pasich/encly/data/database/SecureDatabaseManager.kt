package com.pasich.encly.data.database

import android.content.Context
import androidx.room.useWriterConnection
import com.pasich.encly.core.AppLogger
import com.pasich.encly.core.di.ApplicationContext
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.pasich.encly.core.security.cipher.SQLCipherUtils
import com.pasich.encly.core.security.cipher.getDatabaseState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.pasichdev.sealite.SealiteDriver
import io.github.pasichdev.sealite.SealiteKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.MessageDigest

/**
 * The two database files a vault can have: the real one, and the decoy vault the duress PIN
 * opens (its own file, its own random DEK). At most one of them is open at a time.
 */
enum class VaultFile(internal val fileName: String) {
    PRIMARY("database.db"),
    DECOY("vault_b.db"),
}

/**
 * Owns the SQLCipher-encrypted Room database and its unlocked lifecycle.
 *
 * A committed vault must already have an encrypted database file. Database creation is allowed
 * only during the atomic first-run setup path (and the first opening of a decoy vault).
 */
@SingleIn(AppScope::class)
@Inject
class SecureDatabaseManager(@param:ApplicationContext private val context: Context) : DatabaseProvider {
    private var database: AppDatabase? = null
    private var isUnlocked = false
    private var lease: DatabaseLease? = null
    private var openFile: VaultFile? = null

    /**
     * The driver of the open [database]. It holds its own copy of the key and keys every new
     * pool connection from it (WAL readers, reopen after a pool reset); closing it after the
     * database wipes that copy.
     */
    private var driver: SealiteDriver? = null

    /** A copy of the open database's key, only to check a repeated [unlockDatabase]; wiped in [reset]. */
    private var openKey: ByteArray? = null

    fun isDatabaseUnlocked(): Boolean = isUnlocked

    fun hasEncryptedDatabase(file: VaultFile = VaultFile.PRIMARY): Boolean =
        SQLCipherUtils.getDatabaseState(context, file.fileName) == SQLCipherUtils.State.ENCRYPTED

    @Synchronized
    override fun captureLease(): DatabaseLease? = lease

    @Synchronized
    override fun getDatabase(lease: DatabaseLease): AppDatabase = database?.takeIf {
        isUnlocked && this.lease === lease
    } ?: error("Database session expired")

    /**
     * Opens the vault with the raw 32-byte [dek]; the caller keeps (and wipes) [dek].
     *
     * The key reaches SQLCipher as a raw key ([SealiteKey.raw]), so SQLCipher uses it as the page
     * key directly instead of running its own PBKDF2 over it on every connection: the DEK is
     * already a uniformly random 256-bit key. It is the same key derivation the files were
     * written with before (`x'<64 hex>'` through sqlcipher-android), so existing vaults open.
     *
     * While open, a second call succeeds only with the same key (compared in constant time) and
     * the same [file]; any other key or file is refused, never silently accepted.
     */
    @Synchronized
    fun unlockDatabase(dek: ByteArray, allowCreate: Boolean = false): Boolean =
        unlockDatabase(dek, allowCreate, VaultFile.PRIMARY)

    /** [unlockDatabase] for [file]: the real vault's database or the decoy vault's. */
    @Suppress("ReturnCount") // Fail-closed vault-state/key gates are clearer as early exits.
    @Synchronized
    fun unlockDatabase(dek: ByteArray, allowCreate: Boolean, file: VaultFile): Boolean {
        if (dek.size != DEK_LENGTH) return false
        if (isUnlocked) {
            return openFile == file && openKey?.let { MessageDigest.isEqual(it, dek) } == true
        }

        var openedDatabase: AppDatabase? = null
        var openedDriver: SealiteDriver? = null
        return try {
            when (SQLCipherUtils.getDatabaseState(context, file.fileName)) {
                SQLCipherUtils.State.DOES_NOT_EXIST -> if (!allowCreate) return false

                // Never silently adopt/migrate a plaintext database into a committed vault.
                SQLCipherUtils.State.UNENCRYPTED -> return false

                SQLCipherUtils.State.ENCRYPTED -> Unit
            }

            // No destructive fallback: a schema change without a migration must fail to open
            // (and be caught in tests), never drop the user's notes.
            // The driver keeps its own copy of the key; this one is wiped at once.
            val key = SealiteKey.raw(dek)
            val newDriver = try {
                SealiteDriver(key)
            } finally {
                key.close()
            }
            openedDriver = newDriver
            val opened = VaultSchema.databaseBuilder(context.applicationContext, file.fileName)
                .setDriver(newDriver)
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
            openedDatabase = opened

            // One open, which is also the key check: a wrong key, a damaged file or a failed
            // migration throws here, before the database is published as unlocked.
            runBlocking { opened.useWriterConnection { } }

            database = opened
            driver = newDriver
            openKey = dek.copyOf()
            isUnlocked = true
            openFile = file
            lease = DatabaseLease()
            true
        } catch (e: Exception) {
            openedDatabase?.close()
            openedDriver?.close()
            database = null
            isUnlocked = false
            openFile = null
            lease = null
            AppLogger.e(TAG, "Failed to unlock the database", e)
            false
        }
    }

    private fun deleteDatabaseFiles(file: VaultFile) {
        try {
            val dbFile = context.getDatabasePath(file.fileName)
            File(dbFile.absolutePath).delete()
            File(dbFile.absolutePath + "-wal").delete()
            File(dbFile.absolutePath + "-shm").delete()
            File(dbFile.absolutePath + "-journal").delete()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to delete database files", e)
        }
    }

    @Synchronized
    fun reset() {
        lease = null
        database?.close()
        database = null
        // After the database: closing the driver wipes its copy of the key.
        driver?.close()
        driver = null
        isUnlocked = false
        openFile = null
        openKey?.let(SensitiveDataCleaner::clear)
        openKey = null
    }

    /** Closes and deletes both databases: the real vault's and the decoy vault's. */
    @Synchronized
    fun wipe() {
        reset()
        VaultFile.entries.forEach(::deleteDatabaseFiles)
    }

    /**
     * Deletes the database files unless the database is open, checked and done under the same
     * lock [unlockDatabase] holds, so a database being opened (or just created) meanwhile is
     * never deleted. True when they were deleted.
     *
     * With [only], just that file goes, refused only while that one is open: the decoy vault's
     * database is deleted from a session of the real vault, whose file is never touched.
     */
    @Synchronized
    fun wipeIfClosed(only: VaultFile? = null): Boolean {
        if (isUnlocked && (only == null || openFile == only)) return false
        (only?.let(::listOf) ?: VaultFile.entries).forEach(::deleteDatabaseFiles)
        return true
    }

    companion object {
        private const val TAG = "SecureDatabaseManager"
        private const val DEK_LENGTH = 32
    }
}
