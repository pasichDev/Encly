package com.pasich.encly.domain.usecase.tag

import com.pasich.encly.core.common.UiState
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.repository.TagsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject


class GetTagsUseCase @Inject constructor(
    private val repository: TagsRepository
) {

    operator fun invoke(): Flow<UiState<List<Tag>>> = flow {
        emit(UiState.Loading())
        try {
            repository.getTags().map { tags -> tags.sortedBy() { it.position } }
                .collect { sortedTags ->
                    emit(UiState.Success(sortedTags))
                }
        } catch (e: Exception) {
            emit(UiState.Error(message = e.message.toString()))
        }
    }.flowOn(Dispatchers.IO)
}
