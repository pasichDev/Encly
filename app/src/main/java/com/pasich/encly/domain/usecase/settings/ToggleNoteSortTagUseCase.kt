package com.pasich.encly.domain.usecase.settings

import com.pasich.encly.data.repository.SettingsRepository
import com.pasich.encly.domain.enums.NoteSortOption
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

class ToggleNoteSortTagUseCase @Inject constructor(private val repository: SettingsRepository) {
    operator fun invoke(option: NoteSortOption, scope: CoroutineScope) {
        repository.setSortNotes(option, scope)
    }
}

