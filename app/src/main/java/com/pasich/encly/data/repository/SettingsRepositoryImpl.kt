package com.pasich.encly.data.repository

import com.pasich.encly.data.datasource.local.SettingsLocalDataSource
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.model.FontStyleType
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.domain.model.ThemeType
import com.pasich.encly.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(private val settingsLocalDataSource: SettingsLocalDataSource) :
    SettingsRepository {

    override val themeSettingsFlow: Flow<ThemeSettings> = settingsLocalDataSource.themeSettingsFlow
    override val isGridNoteList: Flow<Boolean> = settingsLocalDataSource.isGridNoteListFlow
    override val getSortNotes: Flow<NoteSortOption> = settingsLocalDataSource.getSortNotes
    override val showTasksFlow: Flow<Boolean> = settingsLocalDataSource.showTasksFlow
    override val simpleEditFlow: Flow<Boolean> = settingsLocalDataSource.simpleEditFlow
    override val fontSizeFlow: Flow<Int> = settingsLocalDataSource.fontSizeFlow
    override val fontStyleFlow: Flow<FontStyleType> = settingsLocalDataSource.fontStyleFlow

    override suspend fun setDynamicTheme(value: Boolean) = settingsLocalDataSource.setDynamicTheme(value)

    override suspend fun setThemeType(value: ThemeType) = settingsLocalDataSource.setThemeType(value)

    override suspend fun setGridNoteList(value: Boolean) = settingsLocalDataSource.setGridNoteList(value)

    override suspend fun setSortNotes(option: NoteSortOption) = settingsLocalDataSource.setSortNotes(option)

    override suspend fun setShowTasks(value: Boolean) = settingsLocalDataSource.setShowTasks(value)

    override suspend fun setSimpleEdit(value: Boolean) = settingsLocalDataSource.setSimpleEdit(value)

    override suspend fun setFontSize(value: Int) = settingsLocalDataSource.setFontSize(value)

    override suspend fun setFontStyle(value: FontStyleType) = settingsLocalDataSource.setFontStyle(value)
}
