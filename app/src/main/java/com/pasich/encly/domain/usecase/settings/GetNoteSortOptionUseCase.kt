package com.pasich.encly.domain.usecase.settings


import com.pasich.encly.data.repository.SettingsRepository
import com.pasich.encly.domain.enums.NoteSortOption
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetNoteSortOptionUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    operator fun invoke(): Flow<NoteSortOption> = repository.getSortNotes
}
