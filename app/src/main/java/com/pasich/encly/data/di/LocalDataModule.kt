package com.pasich.encly.data.di

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.pasich.encly.data.datasource.local.SettingsLocalDataSource
import com.pasich.encly.data.repository.SettingsRepository
import com.pasich.encly.domain.repository.SettingsRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("my_notes_settings")

@Module
@InstallIn(SingletonComponent::class)
object LocalDataModule {

    @Provides
    @Singleton
    fun provideContext(application: Application): Context =
        application.applicationContext

    @Provides
    @Singleton
    fun provideDataStore(context: Context): DataStore<Preferences> =
        context.dataStore

    @Provides
    @Singleton
    fun provideSettingsLocalDataSource(
        dataStore: DataStore<Preferences>
    ): SettingsLocalDataSource = SettingsLocalDataSource(dataStore)

    @Provides
    @Singleton
    fun provideSettingsRepository(
        settingsLocalDataSource: SettingsLocalDataSource
    ): SettingsRepository = SettingsRepositoryImpl(settingsLocalDataSource)
}
