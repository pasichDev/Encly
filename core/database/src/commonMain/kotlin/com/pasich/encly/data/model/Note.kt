package com.pasich.encly.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * [uid] is the note's stable, device-independent identity used by encrypted backups (merge
 * skips a uid that already exists). Code never assigns it: a blank uid is filled with a random
 * one by the database on insert and restored on update (see VaultSchema).
 */
@Entity(tableName = "notes", indices = [Index(value = ["uid"], unique = true)])
public data class Note(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var title: String = "",
    var value: String = "",
    var description: String = "",
    var date: Long = 0L,
    var dateCreate: Long = 0L,
    @Ignore var isChecked: Boolean = false,
    var isTrash: Boolean = false,
    @ColumnInfo(defaultValue = "''")
    var uid: String = "",
    @ColumnInfo(defaultValue = "0")
    var isPinned: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    var isArchived: Boolean = false,
    /** A [com.pasich.encly.domain.model.NoteColor] key, never an ARGB value. */
    @ColumnInfo(defaultValue = "'none'")
    var color: String = NO_COLOR,
    /** The note's own text size in sp, or null to follow the default from Settings. */
    var textSize: Int? = null,
    /** The note's own line spacing (a `NoteLineSpacing` ordinal), or null to follow the default. */
    var lineSpacing: Int? = null,
    /**
     * When the note went to the trash (epoch millis), or null while it is not in the trash. The trash empties a
     * note 28 days after this; a trashed note without it (an imported one) gets the time of the next check.
     */
    var trashedAt: Long? = null,
    /**
     * The ids of the note's tags, as the editor holds them. Not a column: the links live in `note_tags`
     * ([NoteTag]), and a note read straight from `notes` has none here. Only a read that loads the links
     * (the repository's) fills it, and only a write that stores the links saves it.
     */
    @Ignore var tagIds: Set<Long> = emptySet(),
) {

    /**
     * Returns true if the note contains neither a title nor content.
     */
    public fun isEmpty(): Boolean = title.isBlank() && value.isBlank()

    /**
     * Checks whether there are changes in the note's content or title.
     */
    public fun hasContentChanged(newBlocksJson: String, newTitle: String): Boolean {
        if (title != newTitle) {
            return true
        }
        // Check for changes in the block content
        return value != newBlocksJson
    }

    public companion object {
        /** The stored key of "no colour"; the palette lives in `domain.model.NoteColor`. */
        public const val NO_COLOR: String = "none"

        public fun new(title: String, value: String, tagIds: Set<Long> = emptySet()): Note = Note(
            title = title,
            value = value,
            date = nowMillis(),
            dateCreate = nowMillis(),
            tagIds = tagIds,
        )
    }
}
