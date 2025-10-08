package com.pasich.encly.domain.usecase.note

import com.pasich.encly.core.common.UiState
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.data.repository.NotesRepository
import com.pasich.encly.domain.enums.NoteSortOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject


class GetAllNotesUseCase @Inject constructor(
    private val repository: NotesRepository
) {
    operator fun invoke(sortOption: NoteSortOption): Flow<UiState<List<NoteWithTag>>> = flow {
        emit(UiState.Loading())
        try {
            repository.getAllNotesWithTag()
                .map { notesWithTags ->
                    val filtered = notesWithTags.filter { it.tag == null || it.tag.isVisible }

                    val sorted = when (sortOption) {
                        NoteSortOption.CREATED_ASC -> filtered.sortedBy { it.note.dateCreate }
                        NoteSortOption.CREATED_DESC -> filtered.sortedByDescending { it.note.dateCreate }
                        NoteSortOption.UPDATED_ASC -> filtered.sortedBy { it.note.date }
                        NoteSortOption.UPDATED_DESC -> filtered.sortedByDescending { it.note.date }
                    }

                    sorted
                }
                .collect { sortedNotes ->
                    emit(UiState.Success(sortedNotes))
                }
        } catch (e: Exception) {
            emit(UiState.Error(message = e.message.toString()))
        }
    }.flowOn(Dispatchers.IO)
}

