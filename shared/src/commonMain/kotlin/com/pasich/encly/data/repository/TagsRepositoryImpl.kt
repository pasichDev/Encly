package com.pasich.encly.data.repository

import com.pasich.encly.data.database.DatabaseLease
import com.pasich.encly.data.database.DatabaseProvider
import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.repository.TagsRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "TagsRepository"

/** Bound to the opening that created it; see [NotesRepositoryImpl]. */
@Inject
class TagsRepositoryImpl(private val databaseProvider: DatabaseProvider) : TagsRepository {
    private val lease: DatabaseLease? = databaseProvider.captureLease()

    private fun dao() = databaseProvider.getDatabase(lease ?: error("Vault locked")).tagsDao()

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
