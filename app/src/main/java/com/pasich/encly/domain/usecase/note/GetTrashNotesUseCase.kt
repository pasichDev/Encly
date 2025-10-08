package com.pasich.encly.domain.usecase.note

import com.pasich.encly.core.common.UiState
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.repository.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject


class GetTrashNotesUseCase @Inject constructor(
    private val repository: NotesRepository
) {

    operator fun invoke(): Flow<UiState<List<Note>>> = flow {
        emit(UiState.Loading())
        try {
            repository.getTrashNotes().collect { notes ->
                emit(UiState.Success(notes))
            }
        } catch (e: Exception) {
            emit(UiState.Error(message = e.message.toString()))
        }
    }.flowOn(Dispatchers.IO)
}
