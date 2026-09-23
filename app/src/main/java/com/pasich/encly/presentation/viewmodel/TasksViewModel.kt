package com.pasich.encly.presentation.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.data.model.Task
import com.pasich.encly.domain.repository.TasksRepository
import com.pasich.encly.domain.usecase.task.UpdateTaskStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

// Filters for tasks
data class TaskFilter(
    val id: String,
    /** Resolved in the UI so the chip follows the in-app language. */
    @param:StringRes val label: Int,
    val count: Int = 0,
    val type: Type,
) {
    enum class Type {
        ACTIVE,
        PRIORITY,
        COMPLETED,
    }
}

enum class TaskOperationFailure {
    CREATE,
    UPDATE,
    STATUS_UPDATE,
    CLEAR_COMPLETED,
    DELETE,
}

/** Unsaved content of the task editor sheet. */
data class TaskDraft(val title: String, val description: String, val priority: Int)

data class TasksUiState(
    val activeTasks: List<Task> = emptyList(),
    val completedTasks: List<Task> = emptyList(),
    val activeTasksCount: Int = 0,
    val completedTasksCount: Int = 0,
    val totalTasksCount: Int = 0,
    val completionPercentage: Int = 0,
    val availableFilters: List<TaskFilter> = emptyList(),
    val selectedActiveFilter: TaskFilter? = null,
    val selectedPriorityFilter: TaskFilter? = null,
    val selectedCompletedFilter: TaskFilter? = null,
    val filteredActiveTasks: List<Task> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val tasksRepository: TasksRepository,
    private val updateTaskStatusUseCase: UpdateTaskStatusUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    private val _operationFailures = MutableSharedFlow<TaskOperationFailure>(extraBufferCapacity = 1)
    val operationFailures: SharedFlow<TaskOperationFailure> = _operationFailures.asSharedFlow()

    /** A task that was just deleted, so the screen can offer to undo it. */
    private val _deletedTasks = MutableSharedFlow<Task>(extraBufferCapacity = 1)
    val deletedTasks: SharedFlow<Task> = _deletedTasks.asSharedFlow()

    private val _showAddTaskDialog = MutableStateFlow(false)
    val showAddTaskDialog: StateFlow<Boolean> = _showAddTaskDialog.asStateFlow()

    private val _editingTask = MutableStateFlow<Task?>(null)
    val editingTask: StateFlow<Task?> = _editingTask.asStateFlow()

    /** Serializes background draft saves so two quick pauses cannot insert twice. */
    private val draftMutex = Mutex()

    init {
        observeTasks()
    }

    private fun observeTasks() {
        viewModelScope.launch {
            combine(
                tasksRepository.getAllActiveTasks(),
                tasksRepository.getAllCompletedTasks(),
                tasksRepository.getActiveTasksCount(),
                tasksRepository.getCompletedTasksCount(),
            ) { activeTasks, completedTasks, activeCount, completedCount ->
                TaskFilterEngine.reduce(_uiState.value, activeTasks, completedTasks, activeCount, completedCount)
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun showAddTaskDialog() {
        _editingTask.value = null
        _showAddTaskDialog.value = true
    }

    fun showEditTaskDialog(task: Task) {
        _editingTask.value = task
        _showAddTaskDialog.value = true
    }

    fun hideAddTaskDialog() {
        _showAddTaskDialog.value = false
        _editingTask.value = null
    }

    fun addTask(title: String, description: String?, priority: Int, categoryId: Long? = null) {
        viewModelScope.launch {
            val task = Task.new(
                title = title,
                description = description,
                priority = priority,
                categoryId = categoryId,
            )
            if (tasksRepository.insertTask(task).isSuccess) {
                hideAddTaskDialog()
            } else {
                _operationFailures.emit(TaskOperationFailure.CREATE)
            }
        }
    }

    fun editTask(taskId: Long, title: String, description: String?, priority: Int, categoryId: Long? = null) {
        viewModelScope.launch {
            val existingTask = uiState.value.activeTasks.find { it.id == taskId }
                ?: uiState.value.completedTasks.find { it.id == taskId }

            if (existingTask == null) {
                _operationFailures.emit(TaskOperationFailure.UPDATE)
                return@launch
            }

            val updatedTask = existingTask.copy(
                title = title,
                description = description,
                priority = priority,
                // The editor does not show the category: keep the stored one.
                categoryId = categoryId ?: existingTask.categoryId,
            )

            if (tasksRepository.updateTask(updatedTask).isSuccess) {
                hideAddTaskDialog()
            } else {
                _operationFailures.emit(TaskOperationFailure.UPDATE)
            }
        }
    }

    /**
     * Persists the open editor when the app leaves the foreground.
     *
     * Backgrounding re-locks the vault and the re-lock drops every screen, so unsaved sheet
     * input would be lost. The draft is written through the encrypted database before the
     * vault closes. A new task becomes the edited task, so the sheet (if the user returns
     * before the re-lock) keeps editing that row instead of inserting a duplicate.
     */
    fun saveDraftForBackground(draft: TaskDraft) {
        if (draft.title.isBlank() || !_showAddTaskDialog.value) return
        viewModelScope.launch {
            draftMutex.withLock { persistDraft(draft) }
        }
    }

    private suspend fun persistDraft(draft: TaskDraft) {
        val description = draft.description.ifBlank { null }
        val editing = _editingTask.value
        if (editing == null) {
            val task = Task.new(
                title = draft.title,
                description = description,
                priority = draft.priority,
            )
            tasksRepository.insertTask(task)
                .onSuccess { id -> _editingTask.value = task.copy(id = id) }
                .onFailure { _operationFailures.emit(TaskOperationFailure.CREATE) }
        } else {
            val updated = editing.copy(
                title = draft.title,
                description = description,
                priority = draft.priority,
            )
            if (updated == editing) return
            if (tasksRepository.updateTask(updated).isSuccess) {
                _editingTask.value = updated
            } else {
                _operationFailures.emit(TaskOperationFailure.UPDATE)
            }
        }
    }

    fun toggleTaskCompletion(taskId: Long, isCompleted: Boolean) {
        viewModelScope.launch {
            if (updateTaskStatusUseCase(taskId, isCompleted).isFailure) {
                _operationFailures.emit(TaskOperationFailure.STATUS_UPDATE)
            }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            if (tasksRepository.deleteTaskById(task.id).isSuccess) {
                hideAddTaskDialog()
                _deletedTasks.emit(task)
            } else {
                _operationFailures.emit(TaskOperationFailure.DELETE)
            }
        }
    }

    /** Undo for [deleteTask]: puts the same task (id, uid, dates) back. */
    fun restoreTask(task: Task) {
        viewModelScope.launch {
            if (tasksRepository.insertTask(task).isFailure) {
                _operationFailures.emit(TaskOperationFailure.CREATE)
            }
        }
    }

    fun clearCompletedTasks(onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (tasksRepository.deleteAllCompletedTasks().isSuccess) {
                onSuccess()
            } else {
                _operationFailures.emit(TaskOperationFailure.CLEAR_COMPLETED)
            }
        }
    }

    fun onFilterSelected(filter: TaskFilter) {
        _uiState.value = TaskFilterEngine.select(_uiState.value, filter)
    }
}
