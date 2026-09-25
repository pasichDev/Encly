package com.pasich.encly.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema pieces Room cannot express in entity annotations.
 *
 * Every note, tag and task carries a stable `uid` (encrypted backups merge by it). Kotlin code
 * never assigns one, so two triggers keep the invariant "uid is never blank" in the database
 * itself: a blank uid is replaced with 128 random bits on insert, and an update that would
 * blank an existing uid (an entity built without it) restores the old value.
 */
object VaultSchema {
    private val TABLES = listOf("notes", "tags", "tasks")

    private const val RANDOM_UID = "lower(hex(randomblob(16)))"

    /** Database version that dropped `tasks.reminderDate`. */
    private const val VERSION_WITHOUT_TASK_REMINDERS = 3

    private fun triggers(table: String) = listOf(
        "CREATE TRIGGER IF NOT EXISTS `${table}_uid_on_insert` AFTER INSERT ON `$table` " +
            "WHEN NEW.uid = '' BEGIN " +
            "UPDATE `$table` SET uid = $RANDOM_UID WHERE id = NEW.id; END",
        "CREATE TRIGGER IF NOT EXISTS `${table}_uid_keep` AFTER UPDATE OF uid ON `$table` " +
            "WHEN NEW.uid = '' AND OLD.uid <> '' BEGIN " +
            "UPDATE `$table` SET uid = OLD.uid WHERE id = NEW.id; END",
    )

    fun createTriggers(db: SupportSQLiteDatabase) {
        TABLES.flatMap(::triggers).forEach(db::execSQL)
    }

    /** 1 -> 2: add the stable `uid` to notes, tags and tasks and back-fill existing rows. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            TABLES.forEach { table ->
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `$table` SET uid = $RANDOM_UID WHERE uid = ''")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_uid` ON `$table` (`uid`)")
            }
            createTriggers(db)
        }
    }

    /**
     * 2 -> 3: drop `tasks.reminderDate` (task reminders were removed from the app).
     *
     * SQLite only has `DROP COLUMN` from 3.35 and Room's schema test runs on the platform
     * SQLite, so the table is rebuilt instead. Dropping the old table drops its uid triggers
     * and index; both are recreated on the new one.
     */
    val MIGRATION_2_3 = object : Migration(2, VERSION_WITHOUT_TASK_REMINDERS) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val columns = "`id`, `title`, `description`, `isCompleted`, `createdDate`, " +
                "`completedDate`, `priority`, `categoryId`, `position`, `uid`"
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `tasks_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                    "`description` TEXT, `isCompleted` INTEGER NOT NULL, `createdDate` INTEGER NOT NULL, " +
                    "`completedDate` INTEGER, `priority` INTEGER NOT NULL, `categoryId` INTEGER, " +
                    "`position` INTEGER NOT NULL, `uid` TEXT NOT NULL DEFAULT '')",
            )
            db.execSQL("INSERT INTO `tasks_new` ($columns) SELECT $columns FROM `tasks`")
            db.execSQL("DROP TABLE `tasks`")
            db.execSQL("ALTER TABLE `tasks_new` RENAME TO `tasks`")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tasks_uid` ON `tasks` (`uid`)")
            triggers("tasks").forEach(db::execSQL)
        }
    }

    /**
     * The builder for the vault database: every migration and the trigger callback, and no
     * destructive fallback. Room's default (`requireMigration`) makes a missing migration
     * fail loudly instead of recreating the tables, which would erase every note.
     */
    fun databaseBuilder(context: Context, name: String): RoomDatabase.Builder<AppDatabase> =
        install(Room.databaseBuilder(context, AppDatabase::class.java, name))

    /**
     * Adds every migration and the trigger callback to a vault database builder. List each
     * new migration here explicitly.
     */
    fun <T : RoomDatabase> install(builder: RoomDatabase.Builder<T>): RoomDatabase.Builder<T> =
        builder.addMigrations(MIGRATION_1_2, MIGRATION_2_3).addCallback(CALLBACK)

    /** Installs the triggers on a freshly created database. */
    val CALLBACK = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            createTriggers(db)
        }
    }
}
