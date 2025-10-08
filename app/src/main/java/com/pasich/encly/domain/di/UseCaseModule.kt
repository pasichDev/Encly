package com.pasich.encly.domain.di


import com.pasich.encly.data.datasource.local.SettingsLocalDataSource
import com.pasich.encly.data.repository.NotesRepository
import com.pasich.encly.data.repository.TagsRepository
import com.pasich.encly.data.repository.TasksRepository
import com.pasich.encly.domain.repository.TagSelectionRepository
import com.pasich.encly.domain.usecase.note.CleanTrashNotesUseCase
import com.pasich.encly.domain.usecase.note.DeleteNoteByIdUseCase
import com.pasich.encly.domain.usecase.note.GetAllNotesUseCase
import com.pasich.encly.domain.usecase.note.GetNotesByTagUseCase
import com.pasich.encly.domain.usecase.note.GetTrashNotesUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTagUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.domain.usecase.settings.FontSizeUseCase
import com.pasich.encly.domain.usecase.settings.FontStyleUseCase
import com.pasich.encly.domain.usecase.tag.AddTagUseCase
import com.pasich.encly.domain.usecase.tag.DeleteTagUseCase
import com.pasich.encly.domain.usecase.tag.GetTagsUseCase
import com.pasich.encly.domain.usecase.tag.ReorderTagsUseCase
import com.pasich.encly.domain.usecase.tag.SelectTagUseCase
import com.pasich.encly.domain.usecase.tag.UpdateTagUseCase
import com.pasich.encly.domain.usecase.task.AddTaskUseCase
import com.pasich.encly.domain.usecase.task.DeleteCompletedTasksUseCase
import com.pasich.encly.domain.usecase.task.DeleteTaskUseCase
import com.pasich.encly.domain.usecase.task.GetActiveTasksUseCase
import com.pasich.encly.domain.usecase.task.GetCompletedTasksUseCase
import com.pasich.encly.domain.usecase.task.GetTasksCountUseCase
import com.pasich.encly.domain.usecase.task.GetTasksWithReminderUseCase
import com.pasich.encly.domain.usecase.task.UpdateTaskStatusUseCase
import com.pasich.encly.domain.usecase.task.UpdateTaskUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun provideAddTagUseCase(repository: TagsRepository): AddTagUseCase = AddTagUseCase(repository)

    @Provides
    @Singleton
    fun provideGetAllTagsUseCase(repository: TagsRepository): GetTagsUseCase =
        GetTagsUseCase(repository)

    @Provides
    @Singleton
    fun provideDeleteTagsUseCase(repository: TagsRepository): DeleteTagUseCase =
        DeleteTagUseCase(repository)

    @Provides
    @Singleton
    fun provideUpdateTagsUseCase(repository: TagsRepository): UpdateTagUseCase =
        UpdateTagUseCase(repository)

    @Provides
    @Singleton
    fun provideReorderTagsUseCase(repository: TagsRepository): ReorderTagsUseCase =
        ReorderTagsUseCase(repository)

    @Provides
    @Singleton
    fun provideSelectTagUseCase(repository: TagSelectionRepository): SelectTagUseCase =
        SelectTagUseCase(repository)

    @Provides
    @Singleton
    fun provideGetAllNotesUseCase(repository: NotesRepository): GetAllNotesUseCase =
        GetAllNotesUseCase(repository)

    @Provides
    @Singleton
    fun provideGetNotesByTagUseCase(repository: NotesRepository): GetNotesByTagUseCase =
        GetNotesByTagUseCase(repository)

    @Provides
    @Singleton
    fun provideGetTrashNotesUseCase(repository: NotesRepository): GetTrashNotesUseCase =
        GetTrashNotesUseCase(repository)

    @Provides
    @Singleton
    fun provideDeleteNotesUseCase(
        repository: NotesRepository
    ): DeleteNoteByIdUseCase = DeleteNoteByIdUseCase(repository)

    @Provides
    @Singleton
    fun provideUpdateNotesTagUseCase(repository: NotesRepository): UpdateNoteTagUseCase =
        UpdateNoteTagUseCase(repository)

    @Provides
    @Singleton
    fun provideUpdateNotesTrashStatusUseCase(repository: NotesRepository): UpdateNoteTrashStatusUseCase =
        UpdateNoteTrashStatusUseCase(repository)

    @Provides
    @Singleton
    fun provideGetActiveTasksUseCase(repository: TasksRepository): GetActiveTasksUseCase =
        GetActiveTasksUseCase(repository)

    @Provides
    @Singleton
    fun provideGetCompletedTasksUseCase(repository: TasksRepository): GetCompletedTasksUseCase =
        GetCompletedTasksUseCase(repository)

    @Provides
    @Singleton
    fun provideGetTasksCountUseCase(repository: TasksRepository): GetTasksCountUseCase =
        GetTasksCountUseCase(repository)

    @Provides
    @Singleton
    fun provideAddTaskUseCase(repository: TasksRepository): AddTaskUseCase =
        AddTaskUseCase(repository)

    @Provides
    @Singleton
    fun provideUpdateTaskStatusUseCase(repository: TasksRepository): UpdateTaskStatusUseCase =
        UpdateTaskStatusUseCase(repository)

    @Provides
    @Singleton
    fun provideUpdateTaskUseCase(repository: TasksRepository): UpdateTaskUseCase =
        UpdateTaskUseCase(repository)

    @Provides
    @Singleton
    fun provideDeleteTaskUseCase(repository: TasksRepository): DeleteTaskUseCase =
        DeleteTaskUseCase(repository)

    @Provides
    @Singleton
    fun provideDeleteCompletedTasksUseCase(repository: TasksRepository): DeleteCompletedTasksUseCase =
        DeleteCompletedTasksUseCase(repository)

    @Provides
    @Singleton
    fun provideGetTasksWithReminderUseCase(repository: TasksRepository): GetTasksWithReminderUseCase =
        GetTasksWithReminderUseCase(repository)

    @Provides
    @Singleton
    fun provideCleanTrashNotesUseCase(
        repository: NotesRepository
    ): CleanTrashNotesUseCase = CleanTrashNotesUseCase(repository)

    @Provides
    @Singleton
    fun provideFontSizeUseCase(
        settingsLocalDataSource: SettingsLocalDataSource
    ): FontSizeUseCase = FontSizeUseCase(settingsLocalDataSource)

    @Provides
    @Singleton
    fun provideFontStyleUseCase(
        settingsLocalDataSource: SettingsLocalDataSource
    ): FontStyleUseCase = FontStyleUseCase(settingsLocalDataSource)


}