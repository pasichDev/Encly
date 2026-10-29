package com.pasich.encly.data.database

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * The iOS [DatabasePlatform]: both vault files live in [directory], which the app chooses (a
 * directory inside its sandbox). The directory is created on first use.
 */
public class IosDatabasePlatform(private val directory: Path) : DatabasePlatform {
    override fun databasePath(file: VaultFile): Path {
        SystemFileSystem.createDirectories(directory)
        return Path(directory, file.fileName)
    }

    override fun databaseBuilder(path: Path): RoomDatabase.Builder<AppDatabase> = Room.databaseBuilder<AppDatabase>(
        name = path.toString(),
        factory = { AppDatabaseConstructor.initialize() },
    )
}
