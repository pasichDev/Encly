package com.pasich.encly.data.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.pasich.encly.data.database.dao.BackupDao
import com.pasich.encly.data.database.dao.NotesDao
import com.pasich.encly.data.database.dao.TagsDao
import com.pasich.encly.data.database.dao.TasksDao
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Subtask
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.model.Task

/** Room schema version. Every bump needs a migration and an exported schema in core/database/schemas. */
public const val DB_VERSION: Int = 6

@Database(
    entities = [Note::class, Tag::class, Task::class, Subtask::class],
    version = DB_VERSION,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
public abstract class AppDatabase : RoomDatabase() {
    public abstract fun notesDao(): NotesDao
    public abstract fun tagsDao(): TagsDao
    public abstract fun tasksDao(): TasksDao
    public abstract fun backupDao(): BackupDao
}

/** Room's KSP processor writes the `actual` of every target; Room calls it to construct [AppDatabase]. */
@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
public expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
