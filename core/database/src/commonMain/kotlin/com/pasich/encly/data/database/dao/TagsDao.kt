package com.pasich.encly.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.pasich.encly.data.model.Tag
import kotlinx.coroutines.flow.Flow

@Dao
public interface TagsDao {
    @Query("SELECT * FROM tags")
    public fun getTags(): Flow<List<Tag>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun addTags(tags: List<Tag>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun addTag(tag: Tag): Long

    @Update
    public suspend fun updateTag(tag: Tag): Int

    @Update
    public suspend fun updateTags(tags: List<Tag>): Int

    @Delete
    public suspend fun deleteTag(tag: Tag): Int

    @Query("UPDATE notes SET tagId = NULL WHERE tagId = :tagId")
    public suspend fun detachNotesFromTag(tagId: Long): Int

    /**
     * Deletes [tag] and, in the same transaction, makes its notes untagged, so no note keeps
     * pointing at a tag that no longer exists (and hidden-tag notes do not linger hidden).
     */
    @Transaction
    public suspend fun deleteTagDetachingNotes(tag: Tag): Int {
        detachNotesFromTag(tag.id)
        return deleteTag(tag)
    }
}
