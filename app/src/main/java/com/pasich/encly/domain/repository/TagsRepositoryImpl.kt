package com.pasich.encly.domain.repository

import com.pasich.encly.data.datasource.local.DatabaseLocalDataSource
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.repository.TagsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

class TagsRepositoryImpl @Inject constructor(
    private val databaseLocalDataSource: DatabaseLocalDataSource
) : TagsRepository {

    private val _selectedTagFlow = MutableStateFlow<Tag>(Tag())
    override val selectedTagFlow = _selectedTagFlow.asSharedFlow()

    override suspend fun selectTag(tag: Tag) {
        _selectedTagFlow.emit(tag)
    }

    //Tags
    override fun getTags() = databaseLocalDataSource.getTags()
    override fun addTags(tags: List<Tag>) = databaseLocalDataSource.addTags(tags)
    override fun addTag(tag: Tag) = databaseLocalDataSource.addTag(tag)
    override fun deleteTag(tag: Tag) = databaseLocalDataSource.deleteTag(tag)
    override fun updateTag(tag: Tag) = databaseLocalDataSource.updateTag(tag)
}
