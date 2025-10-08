package com.pasich.encly.domain.usecase.settings

import com.pasich.encly.data.datasource.local.FontStyleType
import com.pasich.encly.data.datasource.local.SettingsLocalDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FontSizeUseCase @Inject constructor(
    private val settingsLocalDataSource: SettingsLocalDataSource
) {
    val fontSizeFlow: Flow<Int> = settingsLocalDataSource.fontSizeFlow
    
    fun setFontSize(size: Int, scope: CoroutineScope) {
        settingsLocalDataSource.setFontSize(size, scope)
    }
}

class FontStyleUseCase @Inject constructor(
    private val settingsLocalDataSource: SettingsLocalDataSource
) {
    val fontStyleFlow: Flow<FontStyleType> = settingsLocalDataSource.fontStyleFlow
    
    fun setFontStyle(style: FontStyleType, scope: CoroutineScope) {
        settingsLocalDataSource.setFontStyle(style, scope)
    }
}

class SimpleEditUseCase @Inject constructor(
    private val settingsLocalDataSource: SettingsLocalDataSource
) {
    val simpleEditFlow: Flow<Boolean> = settingsLocalDataSource.simpleEditFlow


}