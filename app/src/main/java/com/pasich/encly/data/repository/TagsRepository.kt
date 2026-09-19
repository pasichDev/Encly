package com.pasich.encly.data.repository

import com.pasich.encly.data.model.Tag
import kotlinx.coroutines.flow.Flow

interface TagsRepository {

    fun getTags(): Flow<List<Tag>>
    suspend fun addTags(tags: List<Tag>): Boolean
    suspend fun addTag(tag: Tag): Long
    suspend fun deleteTag(tag: Tag): Boolean
    suspend fun updateTag(tag: Tag): Boolean
    suspend fun updateTags(tags: List<Tag>): Boolean

    val selectedTagFlow: Flow<Tag>
    suspend fun selectTag(tag: Tag)
}
