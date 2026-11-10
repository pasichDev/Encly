package com.pasich.encly.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.io.files.Path

/**
 * The Android [DatabasePlatform]: the files are where `Context.getDatabasePath` puts them, in the
 * app's `databases` directory, under the same names as ever (`database.db`, `vault_b.db`), so a vault
 * written by an earlier release is found where it was left.
 *
 * `getDatabasePath` is asked with the bare file name, not a path joined by hand: that is also what
 * makes the `databases` directory exist on a fresh install.
 */
public class AndroidDatabasePlatform(context: Context) : DatabasePlatform {
    private val context: Context = context.applicationContext ?: context

    override fun databasePath(file: VaultFile): Path = Path(context.getDatabasePath(file.fileName).path)

    override fun databaseBuilder(path: Path): RoomDatabase.Builder<AppDatabase> = Room.databaseBuilder<AppDatabase>(
        context = context,
        name = path.toString(),
        factory = { AppDatabaseConstructor.initialize() },
    )
}
