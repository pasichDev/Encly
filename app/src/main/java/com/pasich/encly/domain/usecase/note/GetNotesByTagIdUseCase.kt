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


class GetNotesByTagUseCase(
    private val repository: NotesRepository
) {
    operator fun invoke(idTag: Long, sortOption: NoteSortOption): Flow<UiState<List<NoteWithTag>>> = flow {
        emit(UiState.Loading())
        try {
            repository.getNotesByTagId(idTag)
                .map { notesWithTags ->
                    val sorted = when (sortOption) {
                        NoteSortOption.CREATED_ASC -> notesWithTags.sortedBy { it.note.dateCreate }
                        NoteSortOption.CREATED_DESC -> notesWithTags.sortedByDescending { it.note.dateCreate }
                        NoteSortOption.UPDATED_ASC -> notesWithTags.sortedBy { it.note.date }
                        NoteSortOption.UPDATED_DESC -> notesWithTags.sortedByDescending { it.note.date }
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