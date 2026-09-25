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
data class Note(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var title: String = "",
    var value: String = "",
    var description: String = "",
    var date: Long = 0L,
    var dateCreate: Long = 0L,
    var tagId: Long? = null,
    @Ignore var isChecked: Boolean = false,
    var isTrash: Boolean = false,
    @ColumnInfo(defaultValue = "''")
    var uid: String = "",
) {

    /**
     * Returns true if the note contains neither a title nor content.
     */
    fun isEmpty(): Boolean = title.isBlank() && value.isBlank()

    /**
     * Checks whether there are changes in the note's content or title.
     */
    internal fun hasContentChanged(newBlocksJson: String, newTitle: String): Boolean {
        if (title != newTitle) {
            return true
        }
        // Check for changes in the block content
        return value != newBlocksJson
    }

    companion object {
        fun new(title: String, value: String, tagId: Long? = null): Note = Note(
            title = title,
            value = value,
            date = System.currentTimeMillis(),
            dateCreate = System.currentTimeMillis(),
            tagId = tagId,
        )
    }
}
