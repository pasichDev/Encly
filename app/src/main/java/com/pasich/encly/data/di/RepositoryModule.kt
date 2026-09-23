package com.pasich.encly.data.di

import com.pasich.encly.data.repository.NotesRepositoryImpl
import com.pasich.encly.data.repository.SettingsRepositoryImpl
import com.pasich.encly.data.repository.TagsRepositoryImpl
import com.pasich.encly.data.repository.TasksRepositoryImpl
import com.pasich.encly.domain.repository.NotesRepository
import com.pasich.encly.domain.repository.SettingsRepository
import com.pasich.encly.domain.repository.TagsRepository
import com.pasich.encly.domain.repository.TasksRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindNotesRepository(impl: NotesRepositoryImpl): NotesRepository

    @Binds
    abstract fun bindTagsRepository(impl: TagsRepositoryImpl): TagsRepository

    @Binds
    abstract fun bindTasksRepository(impl: TasksRepositoryImpl): TasksRepository

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
