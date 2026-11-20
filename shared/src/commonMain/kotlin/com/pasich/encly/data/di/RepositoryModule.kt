package com.pasich.encly.data.di

import com.pasich.encly.data.repository.NotesRepositoryImpl
import com.pasich.encly.data.repository.SettingsRepositoryImpl
import com.pasich.encly.data.repository.TagsRepositoryImpl
import com.pasich.encly.data.repository.TasksRepositoryImpl
import com.pasich.encly.domain.repository.NotesRepository
import com.pasich.encly.domain.repository.SettingsRepository
import com.pasich.encly.domain.repository.TagsRepository
import com.pasich.encly.domain.repository.TasksRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo

@ContributesTo(AppScope::class)
@BindingContainer
interface RepositoryModule {
    @Binds
    fun bindNotesRepository(impl: NotesRepositoryImpl): NotesRepository

    @Binds
    fun bindTagsRepository(impl: TagsRepositoryImpl): TagsRepository

    @Binds
    fun bindTasksRepository(impl: TasksRepositoryImpl): TasksRepository

    @Binds
    fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
