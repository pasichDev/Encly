package com.pasich.encly.data.repository

import com.pasich.encly.data.database.DatabaseProvider
import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.repository.TagsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TagsRepository"

/** See [NotesRepositoryImpl] for why the DAO is resolved per call. */
@Singleton
class TagsRepositoryImpl @Inject constructor(private val databaseProvider: DatabaseProvider) : TagsRepository {

    private fun dao() = databaseProvider.getDatabase().tagsDao()

    override fun getTags(): Flow<List<Tag>> = daoFlow { dao().getTags() }.map { tags -> tags.sortedBy { it.position } }

    override suspend fun addTag(tag: Tag): Result<Long> = storageWrite(TAG, "addTag") {
        dao().addTag(tag)
    }

    override suspend fun deleteTag(tag: Tag): Result<Unit> = storageWrite(TAG, "deleteTag") {
        dao().deleteTagDetachingNotes(tag).requireRows()
    }

    override suspend fun updateTag(tag: Tag): Result<Unit> = storageWrite(TAG, "updateTag") {
        dao().updateTag(tag).requireRows()
    }

    override suspend fun updateTags(tags: List<Tag>): Result<Unit> {
        if (tags.isEmpty()) return Result.success(Unit)
        return storageWrite(TAG, "updateTags") {
            dao().updateTags(tags).requireRows(expected = tags.size)
        }
    }
}
