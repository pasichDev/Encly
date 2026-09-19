package com.pasich.encly.domain.di

import com.pasich.encly.data.datasource.local.DatabaseLocalDataSource
import com.pasich.encly.data.repository.NotesRepository
import com.pasich.encly.data.repository.TagsRepository
import com.pasich.encly.data.repository.TasksRepository
import com.pasich.encly.domain.repository.NotesRepositoryImpl
import com.pasich.encly.domain.repository.TagSelectionRepository
import com.pasich.encly.domain.repository.TagsRepositoryImpl
import com.pasich.encly.domain.repository.TasksRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideNotesRepository(
        databaseLocalDataSource: DatabaseLocalDataSource
    ): NotesRepository = NotesRepositoryImpl(databaseLocalDataSource)

    @Provides
    @Singleton
    fun provideTagsRepository(
        databaseLocalDataSource: DatabaseLocalDataSource
    ): TagsRepository = TagsRepositoryImpl(databaseLocalDataSource)

    @Provides
    @Singleton
    fun provideTagSelectionRepository(): TagSelectionRepository =
        TagSelectionRepository()

    @Provides
    @Singleton
    fun provideTasksRepository(
        databaseLocalDataSource: DatabaseLocalDataSource
    ): TasksRepository = TasksRepositoryImpl(databaseLocalDataSource)
}
