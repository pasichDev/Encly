package com.pasich.encly.data.database

import androidx.room.RoomDatabase
import kotlinx.io.files.Path

/**
 * What differs per platform about the vault's database: where its files are and how Room is
 * pointed at one. Everything else (the key, the driver, the lifecycle) is common code.
 *
 * Android binds [AndroidDatabasePlatform], whose files are exactly where the app's database
 * directory has always been; the desktop and iOS targets get the directory from the app.
 */
public interface DatabasePlatform {
    /**
     * The path of [file]'s database. The directory it is in exists when this returns, so a first
     * run can create the database there.
     */
    public fun databasePath(file: VaultFile): Path

    /**
     * A Room builder for the database at [path] (a path from [databasePath]). It carries no driver,
     * migrations or callbacks: [SecureDatabaseManager] adds those, the same on every platform.
     */
    public fun databaseBuilder(path: Path): RoomDatabase.Builder<AppDatabase>
}
