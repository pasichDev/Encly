package com.pasich.encly.data.database

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Schema pieces Room cannot express in entity annotations.
 *
 * Every note, tag, task and sub-task carries a stable `uid` (encrypted backups merge by it).
 * Kotlin code never assigns one, so two triggers keep the invariant "uid is never blank" in the
 * database itself: a blank uid is replaced with 128 random bits on insert, and an update that
 * would blank an existing uid (an entity built without it) restores the old value.
 */
public object VaultSchema {
    /** The tables that got a `uid` in version 2. Fixed: later tables are created with one. */
    private val V2_UID_TABLES = listOf("notes", "tags", "tasks")

    private const val SUBTASKS = "subtasks"

    /** Every table with a `uid` and its triggers, in the current schema. */
    private val TABLES = V2_UID_TABLES + SUBTASKS

    private const val RANDOM_UID = "lower(hex(randomblob(16)))"

    /** Database version that dropped `tasks.reminderDate`. */
    private const val VERSION_WITHOUT_TASK_REMINDERS = 3

    /** Database version that added the `subtasks` table. */
    private const val VERSION_WITH_SUBTASKS = 4

    /** Database version that added pin, archive and colour to notes. */
    private const val VERSION_WITH_NOTE_ORGANIZING = 5

    /** Database version that added a note's own text size and line spacing. */
    private const val VERSION_WITH_NOTE_TEXT_SETTINGS = 6

    /** Database version that added the time a note went to the trash. */
    private const val VERSION_WITH_TRASH_TIME = 7

    /** Database version that moved a note's tag into `note_tags`, so a note can have several. */
    private const val VERSION_WITH_NOTE_TAGS = 8

    private const val NOTES = "notes"

    private fun triggers(table: String) = listOf(
        "CREATE TRIGGER IF NOT EXISTS `${table}_uid_on_insert` AFTER INSERT ON `$table` " +
            "WHEN NEW.uid = '' BEGIN " +
            "UPDATE `$table` SET uid = $RANDOM_UID WHERE id = NEW.id; END",
        "CREATE TRIGGER IF NOT EXISTS `${table}_uid_keep` AFTER UPDATE OF uid ON `$table` " +
            "WHEN NEW.uid = '' AND OLD.uid <> '' BEGIN " +
            "UPDATE `$table` SET uid = OLD.uid WHERE id = NEW.id; END",
    )

    /** The uid triggers of every table in the current schema. */
    internal fun triggerStatements(): List<String> = TABLES.flatMap(::triggers)

    public fun createTriggers(connection: SQLiteConnection) {
        triggerStatements().forEach(connection::execSQL)
    }

    /** 1 -> 2: add the stable `uid` to notes, tags and tasks and back-fill existing rows. */
    public val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            V2_UID_TABLES.forEach { table ->
                connection.execSQL("ALTER TABLE `$table` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''")
                connection.execSQL("UPDATE `$table` SET uid = $RANDOM_UID WHERE uid = ''")
                connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_uid` ON `$table` (`uid`)")
            }
            // Only these tables exist at version 2; later tables get their triggers in their
            // own migration.
            V2_UID_TABLES.flatMap(::triggers).forEach(connection::execSQL)
        }
    }

    /**
     * 2 -> 3: drop `tasks.reminderDate` (task reminders were removed from the app).
     *
     * SQLite only has `DROP COLUMN` from 3.35 and Room's schema test runs on the platform
     * SQLite, so the table is rebuilt instead. Dropping the old table drops its uid triggers
     * and index; both are recreated on the new one.
     */
    public val MIGRATION_2_3: Migration = object : Migration(2, VERSION_WITHOUT_TASK_REMINDERS) {
        override fun migrate(connection: SQLiteConnection) {
            val columns = "`id`, `title`, `description`, `isCompleted`, `createdDate`, " +
                "`completedDate`, `priority`, `categoryId`, `position`, `uid`"
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `tasks_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                    "`description` TEXT, `isCompleted` INTEGER NOT NULL, `createdDate` INTEGER NOT NULL, " +
                    "`completedDate` INTEGER, `priority` INTEGER NOT NULL, `categoryId` INTEGER, " +
                    "`position` INTEGER NOT NULL, `uid` TEXT NOT NULL DEFAULT '')",
            )
            connection.execSQL("INSERT INTO `tasks_new` ($columns) SELECT $columns FROM `tasks`")
            connection.execSQL("DROP TABLE `tasks`")
            connection.execSQL("ALTER TABLE `tasks_new` RENAME TO `tasks`")
            connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tasks_uid` ON `tasks` (`uid`)")
            triggers("tasks").forEach(connection::execSQL)
        }
    }

    /**
     * 3 -> 4: add the `subtasks` table (a task's checklist), with its index on `taskId`, the
     * unique `uid` index and the uid triggers. Existing tasks simply have no sub-tasks. The
     * statements match Room's export of version 4 (core/database/schemas), which the schema test checks.
     */
    public val MIGRATION_3_4: Migration = object : Migration(VERSION_WITHOUT_TASK_REMINDERS, VERSION_WITH_SUBTASKS) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `$SUBTASKS` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `taskId` INTEGER NOT NULL, " +
                    "`title` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                    "`uid` TEXT NOT NULL DEFAULT '', " +
                    "FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_subtasks_taskId` ON `$SUBTASKS` (`taskId`)")
            connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_subtasks_uid` ON `$SUBTASKS` (`uid`)")
            triggers(SUBTASKS).forEach(connection::execSQL)
        }
    }

    /**
     * 4 -> 5: add `isPinned`, `isArchived` and `color` to notes. Plain `ADD COLUMN` with a
     * default, so every existing note stays as it was: unpinned, not archived, no colour. The
     * statements match Room's export of version 5 (core/database/schemas), which the schema test checks.
     */
    public val MIGRATION_4_5: Migration = object : Migration(VERSION_WITH_SUBTASKS, VERSION_WITH_NOTE_ORGANIZING) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `notes` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE `notes` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE `notes` ADD COLUMN `color` TEXT NOT NULL DEFAULT 'none'")
        }
    }

    /**
     * 5 -> 6: add a note's own `textSize` and `lineSpacing`. Both are nullable (null: the note
     * follows the defaults of Settings), so every existing note stays as it was. Version 5 was
     * already out on dev installs without them, so they are a step of their own: editing 5 in
     * place would fail Room's identity check there.
     */
    public val MIGRATION_5_6: Migration = object : Migration(
        VERSION_WITH_NOTE_ORGANIZING,
        VERSION_WITH_NOTE_TEXT_SETTINGS,
    ) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `notes` ADD COLUMN `textSize` INTEGER")
            connection.execSQL("ALTER TABLE `notes` ADD COLUMN `lineSpacing` INTEGER")
        }
    }

    /**
     * 6 -> 7: add `trashedAt`, when a note went to the trash. Nullable, so every note outside the trash
     * stays as it was. A note already in the trash is stamped with the time of the migration: its real
     * trash time was never kept, and the trash must not empty a note the first time it is checked.
     */
    public val MIGRATION_6_7: Migration = object : Migration(
        VERSION_WITH_NOTE_TEXT_SETTINGS,
        VERSION_WITH_TRASH_TIME,
    ) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `notes` ADD COLUMN `trashedAt` INTEGER")
            connection.execSQL(
                "UPDATE `notes` SET `trashedAt` = CAST(strftime('%s', 'now') AS INTEGER) * 1000 WHERE `isTrash` = 1",
            )
        }
    }

    /**
     * 7 -> 8: a note can have several tags. The links move to the new `note_tags` table (one row per note and
     * tag, both foreign keys cascading, an index on `tagId`) and `notes.tagId` goes. Every note keeps the tag it
     * had; a `tagId` that names no tag (left by an old version) was no tag on screen and is dropped, so the
     * foreign key check Room runs after the migration passes.
     *
     * `notes` is rebuilt without the column, like [MIGRATION_2_3] (SQLite only has `DROP COLUMN` from 3.35),
     * keeping every other column, the uid index and the uid triggers. The links are first copied to a plain
     * table and only written to `note_tags` once `notes` is rebuilt, so nothing depends on whether foreign keys
     * are enforced while the migration runs (a `DROP TABLE` of a parent would otherwise cascade). The
     * AUTOINCREMENT counter of `notes` is carried over, so the id of a deleted note is never given out again.
     * The statements match Room's export of version 8 (core/database/schemas), which the schema test checks.
     */
    public val MIGRATION_7_8: Migration = object : Migration(VERSION_WITH_TRASH_TIME, VERSION_WITH_NOTE_TAGS) {
        override fun migrate(connection: SQLiteConnection) {
            val columns = "`id`, `title`, `value`, `description`, `date`, `dateCreate`, `isTrash`, `uid`, " +
                "`isPinned`, `isArchived`, `color`, `textSize`, `lineSpacing`, `trashedAt`"
            connection.execSQL("DROP TABLE IF EXISTS `note_tags_carry`")
            connection.execSQL(
                "CREATE TABLE `note_tags_carry` AS SELECT `id` AS `noteId`, `tagId` FROM `$NOTES` " +
                    "WHERE `tagId` IS NOT NULL AND `tagId` IN (SELECT `id` FROM `tags`)",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `notes_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                    "`value` TEXT NOT NULL, `description` TEXT NOT NULL, `date` INTEGER NOT NULL, " +
                    "`dateCreate` INTEGER NOT NULL, `isTrash` INTEGER NOT NULL, `uid` TEXT NOT NULL DEFAULT '', " +
                    "`isPinned` INTEGER NOT NULL DEFAULT 0, `isArchived` INTEGER NOT NULL DEFAULT 0, " +
                    "`color` TEXT NOT NULL DEFAULT 'none', `textSize` INTEGER, `lineSpacing` INTEGER, " +
                    "`trashedAt` INTEGER)",
            )
            connection.execSQL("INSERT INTO `notes_new` ($columns) SELECT $columns FROM `$NOTES`")
            // The rename below carries this row over to `notes`.
            connection.execSQL("DELETE FROM sqlite_sequence WHERE name = 'notes_new'")
            connection.execSQL(
                "INSERT INTO sqlite_sequence (name, seq) SELECT 'notes_new', seq FROM sqlite_sequence " +
                    "WHERE name = '$NOTES'",
            )
            connection.execSQL("DROP TABLE `$NOTES`")
            connection.execSQL("ALTER TABLE `notes_new` RENAME TO `$NOTES`")
            connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_notes_uid` ON `$NOTES` (`uid`)")
            triggers(NOTES).forEach(connection::execSQL)

            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `note_tags` (`noteId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`noteId`, `tagId`), " +
                    "FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_note_tags_tagId` ON `note_tags` (`tagId`)")
            connection.execSQL(
                "INSERT OR IGNORE INTO `note_tags` (`noteId`, `tagId`) SELECT `noteId`, `tagId` FROM `note_tags_carry`",
            )
            connection.execSQL("DROP TABLE `note_tags_carry`")
        }
    }

    /**
     * Adds every migration and the trigger callback to a vault database builder, and nothing
     * destructive: Room's default (`requireMigration`) makes a missing migration fail loudly
     * instead of recreating the tables, which would erase every note. List each new migration
     * here explicitly.
     */
    public fun <T : RoomDatabase> install(builder: RoomDatabase.Builder<T>): RoomDatabase.Builder<T> = builder
        .addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
        )
        .addCallback(CALLBACK)

    /** Installs the triggers on a freshly created database. */
    public val CALLBACK: RoomDatabase.Callback = object : RoomDatabase.Callback() {
        override fun onCreate(connection: SQLiteConnection) {
            createTriggers(connection)
        }
    }
}
