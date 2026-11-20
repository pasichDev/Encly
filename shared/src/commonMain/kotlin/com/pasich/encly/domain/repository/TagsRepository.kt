package com.pasich.encly.domain.repository

import com.pasich.encly.data.model.Tag
import kotlinx.coroutines.flow.Flow

/** The tags, in their display order. Writes follow the [NotesRepository] error contract. */
interface TagsRepository {
    fun getTags(): Flow<List<Tag>>

    /** The new tag's row id. */
    suspend fun addTag(tag: Tag): Result<Long>

    /** Deletes [tag] and untags its notes. */
    suspend fun deleteTag(tag: Tag): Result<Unit>
    suspend fun updateTag(tag: Tag): Result<Unit>

    /** Updates every tag in [tags], or fails. */
    suspend fun updateTags(tags: List<Tag>): Result<Unit>
}
