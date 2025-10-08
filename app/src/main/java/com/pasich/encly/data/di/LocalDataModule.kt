package com.pasich.encly.data.di

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.pasich.encly.data.database.AppDatabase
import com.pasich.encly.data.database.SecureDatabaseManager
import com.pasich.encly.data.database.dao.NotesDao
import com.pasich.encly.data.database.dao.TagsDao
import com.pasich.encly.data.database.dao.TasksDao
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
    fun provideContext(application: Application): Context {
        return application.applicationContext
    }

    @Provides
    @Singleton
    fun provideDataStore(context: Context): DataStore<Preferences> {
        return context.dataStore
    }


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


    @Provides
    @Singleton
    fun provideAppDatabase(
        secureDatabaseManager: SecureDatabaseManager
    ): AppDatabase {
        return secureDatabaseManager.getDatabase()
    }

    @Provides
    @Singleton
    fun provideNotesDao(db: AppDatabase): NotesDao = db.notesDao()

    @Provides
    @Singleton
    fun provideTagDao(db: AppDatabase): TagsDao = db.tagsDao()

    @Provides
    @Singleton
    fun provideTasksDao(db: AppDatabase): TasksDao = db.tasksDao()


}
