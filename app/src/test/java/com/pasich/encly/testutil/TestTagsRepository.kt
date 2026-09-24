package com.pasich.encly.testutil

import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.repository.TagsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Tags in memory, in display order. [failWrites] makes every write fail. */
internal class TestTagsRepository(initial: List<Tag> = emptyList()) : TagsRepository {
    val tags = MutableStateFlow(initial)
    var failWrites = false

    /** Every list passed to [updateTags], in call order. */
    val savedOrders = mutableListOf<List<Tag>>()

    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override fun getTags(): Flow<List<Tag>> = tags.map { list -> list.sortedBy { it.position } }

    override suspend fun addTag(tag: Tag): Result<Long> {
        if (failWrites) return Result.failure(IllegalStateException("add"))
        val id = nextId++
        tags.value = tags.value + tag.copy(id = id)
        return Result.success(id)
    }

    override suspend fun deleteTag(tag: Tag): Result<Unit> = write {
        tags.value = tags.value.filterNot { it.id == tag.id }
    }

    override suspend fun updateTag(tag: Tag): Result<Unit> = write {
        tags.value = tags.value.map { if (it.id == tag.id) tag else it }
    }

    override suspend fun updateTags(tags: List<Tag>): Result<Unit> = write {
        savedOrders += tags
        this.tags.value = tags
    }

    private inline fun write(block: () -> Unit): Result<Unit> =
        if (failWrites) Result.failure(IllegalStateException("write")) else runCatching(block)
}
