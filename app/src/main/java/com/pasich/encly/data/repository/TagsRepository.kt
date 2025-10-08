package com.pasich.encly.data.repository

import com.pasich.encly.data.model.Tag
import kotlinx.coroutines.flow.Flow

interface TagsRepository {

    // Tags
    fun getTags(): Flow<List<Tag>>
    fun addTags(tags: List<Tag>)
    fun addTag(tag: Tag)
    fun deleteTag(tag: Tag)
    fun updateTag(tag: Tag)

    // Shared selected tag
    val selectedTagFlow: Flow<Tag>
    suspend fun selectTag(tag: Tag)
}