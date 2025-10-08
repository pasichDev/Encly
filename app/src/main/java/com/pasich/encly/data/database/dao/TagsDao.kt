package com.pasich.encly.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pasich.encly.data.model.Tag
import kotlinx.coroutines.flow.Flow

@Dao
interface TagsDao {
    @Query("SELECT * FROM tags")
     fun getTags(): Flow<List<Tag>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
     fun addTags(tags: List<Tag>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
     fun addTag(tag: Tag)

    @Update
     fun updateTag(tag: Tag)

    @Delete
     fun deleteTag(tag: Tag)

}