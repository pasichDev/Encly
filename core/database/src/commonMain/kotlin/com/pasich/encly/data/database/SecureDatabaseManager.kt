package com.pasich.encly.data.database

import androidx.room.useWriterConnection
import com.pasich.encly.core.security.cipher.SQLCipherUtils
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.pasichdev.sealite.SealiteDriver
import io.github.pasichdev.sealite.SealiteKey
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * The two database files a vault can have: the real one, and the decoy vault the duress PIN
 * opens (its own file, its own random DEK). At most one of them is open at a time.
 */
public enum class VaultFile(internal val fileName: String) {
    PRIMARY("database.db"),
    DECOY("vault_b.db"),
}

/**
 * Owns the SQLCipher-encrypted Room database and its unlocked lifecycle.
 *
 * A committed vault must already have an encrypted database file. Database creation is allowed
 * only during the atomic first-run setup path (and the first opening of a decoy vault).
 *
 * Where the files are, and how Room is pointed at one, is the [platform]'s business.
 */
@SingleIn(AppScope::class)
@Inject
public class SecureDatabaseManager(private val platform: DatabasePlatform) : DatabaseProvider {
    /** Guards every field below; reentrant, as [unlockDatabase] and [wipe] call into other guarded functions. */
    private val lock = SynchronizedObject()

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

    public fun isDatabaseUnlocked(): Boolean = isUnlocked

    public fun hasEncryptedDatabase(file: VaultFile = VaultFile.PRIMARY): Boolean =
        SQLCipherUtils.getDatabaseState(platform.databasePath(file)) == SQLCipherUtils.State.ENCRYPTED

    override fun captureLease(): DatabaseLease? = synchronized(lock) { lease }

    override fun getDatabase(lease: DatabaseLease): AppDatabase = synchronized(lock) {
        database?.takeIf { isUnlocked && this.lease === lease } ?: error("Database session expired")
    }

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
    public fun unlockDatabase(dek: ByteArray, allowCreate: Boolean = false): Boolean =
        unlockDatabase(dek, allowCreate, VaultFile.PRIMARY)

    /** [unlockDatabase] for [file]: the real vault's database or the decoy vault's. */
    public fun unlockDatabase(dek: ByteArray, allowCreate: Boolean, file: VaultFile): Boolean =
        synchronized(lock) { unlockLocked(dek, allowCreate, file) }

    @Suppress("ReturnCount") // Fail-closed vault-state/key gates are clearer as early exits.
    private fun unlockLocked(dek: ByteArray, allowCreate: Boolean, file: VaultFile): Boolean {
        if (dek.size != DEK_LENGTH) return false
        if (isUnlocked) {
            return openFile == file && openKey?.let { constantTimeEquals(it, dek) } == true
        }

        var openedDatabase: AppDatabase? = null
        var openedDriver: SealiteDriver? = null
        return try {
            val path = platform.databasePath(file)
            when (SQLCipherUtils.getDatabaseState(path)) {
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
            val opened = VaultSchema.install(platform.databaseBuilder(path))
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
        } catch (_: Exception) {
            // No log: failure events must not carry anything about the vault.
            openedDatabase?.close()
            openedDriver?.close()
            database = null
            isUnlocked = false
            openFile = null
            lease = null
            false
        }
    }

    private fun deleteDatabaseFiles(file: VaultFile) {
        val dbFile = try {
            platform.databasePath(file).toString()
        } catch (_: Exception) {
            return
        }
        // Each file on its own: one that cannot be deleted must not spare the others.
        FILE_SUFFIXES.forEach { suffix ->
            try {
                SystemFileSystem.delete(Path(dbFile + suffix), mustExist = false)
            } catch (_: Exception) {
                // Best effort, as File.delete was: the next wipe tries again.
            }
        }
    }

    public fun reset(): Unit = synchronized(lock) {
        lease = null
        database?.close()
        database = null
        // After the database: closing the driver wipes its copy of the key.
        driver?.close()
        driver = null
        isUnlocked = false
        openFile = null
        openKey?.fill(0)
        openKey = null
    }

    /** Closes and deletes both databases: the real vault's and the decoy vault's. */
    public fun wipe(): Unit = synchronized(lock) {
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
    public fun wipeIfClosed(only: VaultFile? = null): Boolean = synchronized(lock) {
        if (isUnlocked && (only == null || openFile == only)) return@synchronized false
        (only?.let(::listOf) ?: VaultFile.entries).forEach(::deleteDatabaseFiles)
        true
    }

    private companion object {
        const val DEK_LENGTH = 32

        /**
         * The database file itself, the SQLite side files beside it and Room's lock file (which the
         * desktop and iOS targets keep next to the database; Android keeps it elsewhere).
         */
        val FILE_SUFFIXES = listOf("", "-wal", "-shm", "-journal", ".lck")
    }
}

/**
 * Whether [a] and [b] hold the same bytes, in time that depends on their lengths only and not
 * on where they differ (what `MessageDigest.isEqual` does on the JVM).
 */
internal fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    var diff = a.size xor b.size
    for (i in a.indices) {
        val other = if (i < b.size) b[i].toInt() else 0
        diff = diff or (a[i].toInt() xor other)
    }
    return diff == 0
}
