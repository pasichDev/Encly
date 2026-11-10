package com.pasich.encly.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One tag of one note: a note has any number of tags. Deleting the note or the tag deletes the link (the database
 * cascades it), so a link never points at a row that is gone.
 */
@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tagId"])],
)
public data class NoteTag(val noteId: Long, val tagId: Long)

/** How many notes outside the trash (archived ones included) carry the tag [tagId]. */
public data class TagNoteCount(val tagId: Long, val count: Int)
