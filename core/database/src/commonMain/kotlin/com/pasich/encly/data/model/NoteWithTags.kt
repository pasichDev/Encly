package com.pasich.encly.data.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/** A note with its tags. The database gives the tags in no particular order; readers sort them. */
public data class NoteWithTags(
    @Embedded val note: Note,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(NoteTag::class, parentColumn = "noteId", entityColumn = "tagId"),
    )
    val tags: List<Tag>,
)
