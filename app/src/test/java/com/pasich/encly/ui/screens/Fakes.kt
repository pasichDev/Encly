package com.pasich.encly.ui.screens

import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.model.Task
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.model.FontStyleType
import com.pasich.encly.domain.model.ThemePalette
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.domain.model.ThemeType
import com.pasich.encly.domain.repository.SettingsRepository
import com.pasich.encly.domain.repository.TagsRepository
import com.pasich.encly.domain.repository.TasksRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

internal class FakeSettingsRepository : SettingsRepository {
    val theme = MutableStateFlow(ThemeSettings())
    val grid = MutableStateFlow(false)
    val sort = MutableStateFlow(NoteSortOption.UPDATED_DESC)
    val showTasks = MutableStateFlow(true)
    val simpleEdit = MutableStateFlow(false)
    val fontSize = MutableStateFlow(DEFAULT_FONT_SIZE)

    override val themeSettingsFlow: Flow<ThemeSettings> = theme
    override val isGridNoteList: Flow<Boolean> = grid
    override val getSortNotes: Flow<NoteSortOption> = sort
    override val showTasksFlow: Flow<Boolean> = showTasks
    override val simpleEditFlow: Flow<Boolean> = simpleEdit
    override val fontSizeFlow: Flow<Int> = fontSize
    override val fontStyleFlow: Flow<FontStyleType> = theme.map { it.fontStyle }
    override val latestThemeSettings: ThemeSettings get() = theme.value

    override suspend fun loadThemeSettings(existingInstall: Boolean): ThemeSettings = theme.value
    override suspend fun setDynamicTheme(value: Boolean) = theme.update { it.copy(dynamic = value) }
    override suspend fun setThemeType(value: ThemeType) = theme.update { it.copy(type = value) }
    override suspend fun setThemePalette(value: ThemePalette) = theme.update { it.copy(palette = value) }
    override suspend fun setGridNoteList(value: Boolean) {
        grid.value = value
    }

    override suspend fun setSortNotes(option: NoteSortOption) {
        sort.value = option
    }

    override suspend fun setShowTasks(value: Boolean) {
        showTasks.value = value
    }

    override suspend fun setSimpleEdit(value: Boolean) {
        simpleEdit.value = value
    }

    override suspend fun setFontSize(value: Int) {
        fontSize.value = value
    }

    override suspend fun setFontStyle(value: FontStyleType) = theme.update { it.copy(fontStyle = value) }

    private companion object {
        const val DEFAULT_FONT_SIZE = 16
    }
}

internal class FakeTagsRepository(initial: List<Tag> = emptyList()) : TagsRepository {
    val tags = MutableStateFlow(initial)

    override fun getTags(): Flow<List<Tag>> = tags

    override suspend fun addTag(tag: Tag): Result<Long> {
        val id = (tags.value.maxOfOrNull { it.id } ?: 0L) + 1
        tags.update { it + tag.copy(id = id, position = it.size) }
        return Result.success(id)
    }

    override suspend fun deleteTag(tag: Tag): Result<Unit> {
        tags.update { list -> list.filterNot { it.id == tag.id } }
        return Result.success(Unit)
    }

    override suspend fun updateTag(tag: Tag): Result<Unit> {
        tags.update { list -> list.map { if (it.id == tag.id) tag.copy() else it } }
        return Result.success(Unit)
    }

    override suspend fun updateTags(tags: List<Tag>): Result<Unit> {
        this.tags.value = tags.map { it.copy() }
        return Result.success(Unit)
    }
}

internal class FakeTasksRepository(initial: List<Task> = emptyList()) : TasksRepository {
    val tasks = MutableStateFlow(initial)

    override fun getAllActiveTasks(): Flow<List<Task>> = tasks.map { list -> list.filterNot { it.isCompleted } }
    override fun getAllCompletedTasks(): Flow<List<Task>> = tasks.map { list -> list.filter { it.isCompleted } }
    override fun getAllTasks(): Flow<List<Task>> = tasks
    override fun getActiveTasksCount(): Flow<Int> = tasks.map { list -> list.count { !it.isCompleted } }
    override fun getCompletedTasksCount(): Flow<Int> = tasks.map { list -> list.count { it.isCompleted } }

    override suspend fun insertTask(task: Task): Result<Long> {
        val id = (tasks.value.maxOfOrNull { it.id } ?: 0L) + 1
        tasks.update { it + task.copy(id = id) }
        return Result.success(id)
    }

    override suspend fun updateTask(task: Task): Result<Unit> {
        tasks.update { list -> list.map { if (it.id == task.id) task else it } }
        return Result.success(Unit)
    }

    override suspend fun updateTaskStatus(id: Long, isCompleted: Boolean, completedDate: Long?): Result<Unit> {
        tasks.update { list ->
            list.map { if (it.id == id) it.copy(isCompleted = isCompleted, completedDate = completedDate) else it }
        }
        return Result.success(Unit)
    }

    override suspend fun deleteTaskById(id: Long): Result<Unit> {
        tasks.update { list -> list.filterNot { it.id == id } }
        return Result.success(Unit)
    }

    override suspend fun deleteAllCompletedTasks(): Result<Unit> {
        tasks.update { list -> list.filterNot { it.isCompleted } }
        return Result.success(Unit)
    }
}
