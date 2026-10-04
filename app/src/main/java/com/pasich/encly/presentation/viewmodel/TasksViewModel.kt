package com.pasich.encly.presentation.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.security.NeverLocked
import com.pasich.encly.core.security.VaultLockEvents
import com.pasich.encly.data.model.Subtask
import com.pasich.encly.data.model.SubtaskProgress
import com.pasich.encly.data.model.Task
import com.pasich.encly.domain.repository.TasksRepository
import com.pasich.encly.domain.usecase.task.UpdateTaskStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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

/** How many open tasks the notes screen previews. */
const val HOME_WIDGET_TASKS = 2

/**
 * The open tasks the notes screen previews: highest priority first (the stored order within one
 * priority), at most [limit].
 */
fun widgetTasks(tasks: List<Task>, limit: Int = HOME_WIDGET_TASKS): List<Task> =
    tasks.filterNot { it.isCompleted }.sortedByDescending { it.priority }.take(limit)

/**
 * Unsaved content of the task editor sheet. [subtasks] is null while the edited task's
 * checklist has not loaded: the stored one is then left as it is.
 */
data class TaskDraft(
    val title: String,
    val description: String,
    val priority: Int,
    val subtasks: List<SubtaskDraft>? = null,
)

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
    /** Sub-task progress by task id; a task without sub-tasks has no entry. */
    val subtaskProgress: Map<Long, SubtaskProgress> = emptyMap(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val tasksRepository: TasksRepository,
    private val updateTaskStatusUseCase: UpdateTaskStatusUseCase,
    lockEvents: VaultLockEvents = NeverLocked,
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

    /** The edited task's checklist: empty for a new task, null until an edited one has loaded. */
    private val _editingSubtasks = MutableStateFlow<List<SubtaskDraft>?>(emptyList())
    val editingSubtasks: StateFlow<List<SubtaskDraft>?> = _editingSubtasks.asStateFlow()

    /**
     * A task whose last open sub-task was just ticked, so the screen can offer to complete it
     * with one tap. The task is never completed without that tap.
     */
    private val _completionOffers = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val completionOffers: SharedFlow<Long> = _completionOffers.asSharedFlow()

    /** Sub-tasks of deleted tasks by task id, for [restoreTask]; the delete cascades to them. */
    private val deletedSubtasks = mutableMapOf<Long, List<Subtask>>()

    private var subtasksJob: Job? = null

    /** Serializes background draft saves so two quick pauses cannot insert twice. */
    private val draftMutex = Mutex()

    private var tasksJob: Job? = null

    init {
        observeTasks()
        clearOnLock(lockEvents) {
            tasksJob?.cancel()
            subtasksJob?.cancel()
            _uiState.value = TasksUiState()
            _editingTask.value = null
            _editingSubtasks.value = emptyList()
            deletedSubtasks.clear()
            _showAddTaskDialog.value = false
        }
    }

    private fun observeTasks() {
        tasksJob = viewModelScope.launch {
            combine(
                tasksRepository.getAllActiveTasks(),
                tasksRepository.getAllCompletedTasks(),
                tasksRepository.getActiveTasksCount(),
                tasksRepository.getCompletedTasksCount(),
                tasksRepository.getSubtaskProgress(),
            ) { activeTasks, completedTasks, activeCount, completedCount, progress ->
                TaskFilterEngine.reduce(_uiState.value, activeTasks, completedTasks, activeCount, completedCount)
                    .copy(subtaskProgress = progress.associateBy { it.taskId })
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun showAddTaskDialog() {
        subtasksJob?.cancel()
        _editingTask.value = null
        _editingSubtasks.value = emptyList()
        _showAddTaskDialog.value = true
    }

    fun showEditTaskDialog(task: Task) {
        subtasksJob?.cancel()
        _editingTask.value = task
        // Unknown until loaded: a sheet saved before then leaves the stored checklist alone,
        // and one that fails to load never overwrites it with an empty list.
        _editingSubtasks.value = null
        _showAddTaskDialog.value = true
        subtasksJob = viewModelScope.launch {
            tasksRepository.getSubtasks(task.id).onSuccess { _editingSubtasks.value = SubtaskDrafts.fromSubtasks(it) }
        }
    }

    fun hideAddTaskDialog() {
        subtasksJob?.cancel()
        _showAddTaskDialog.value = false
        _editingTask.value = null
        _editingSubtasks.value = emptyList()
    }

    fun addTask(
        title: String,
        description: String?,
        priority: Int,
        subtasks: List<SubtaskDraft> = emptyList(),
        categoryId: Long? = null,
    ) {
        viewModelScope.launch {
            val task = Task.new(
                title = title,
                description = description,
                priority = priority,
                categoryId = categoryId,
            )
            val id = tasksRepository.insertTask(task).getOrNull()
            if (id == null) {
                _operationFailures.emit(TaskOperationFailure.CREATE)
                return@launch
            }
            val rows = SubtaskDrafts.toSubtasks(id, subtasks)
            if (rows.isNotEmpty() && tasksRepository.saveSubtasks(id, rows).isFailure) {
                _operationFailures.emit(TaskOperationFailure.CREATE)
                return@launch
            }
            hideAddTaskDialog()
            if (SubtaskDrafts.offersCompletion(task.isCompleted, emptyList(), rows)) _completionOffers.emit(id)
        }
    }

    /** [subtasks] null leaves the stored checklist as it is. */
    @Suppress("LongParameterList") // The editor's fields, each optional for the callers that lack it.
    fun editTask(
        taskId: Long,
        title: String,
        description: String?,
        priority: Int,
        subtasks: List<SubtaskDraft>? = null,
        categoryId: Long? = null,
    ) {
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

            if (tasksRepository.updateTask(updatedTask).isFailure) {
                _operationFailures.emit(TaskOperationFailure.UPDATE)
                return@launch
            }
            if (subtasks != null && !saveEditedSubtasks(updatedTask, subtasks)) {
                _operationFailures.emit(TaskOperationFailure.UPDATE)
                return@launch
            }
            hideAddTaskDialog()
        }
    }

    /** Stores the edited checklist and, when that ticked the last open sub-task, offers to complete the task. */
    private suspend fun saveEditedSubtasks(task: Task, drafts: List<SubtaskDraft>): Boolean {
        val after = SubtaskDrafts.toSubtasks(task.id, drafts)
        val before = tasksRepository.getSubtasks(task.id).getOrNull()
        val saved = before != null && (before == after || tasksRepository.saveSubtasks(task.id, after).isSuccess)
        if (saved && SubtaskDrafts.offersCompletion(task.isCompleted, before.orEmpty(), after)) {
            _completionOffers.emit(task.id)
        }
        return saved
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
        val taskId = persistDraftTask(draft) ?: return
        val subtasks = draft.subtasks ?: return
        if (tasksRepository.saveSubtasks(taskId, SubtaskDrafts.toSubtasks(taskId, subtasks)).isFailure) {
            _operationFailures.emit(TaskOperationFailure.UPDATE)
        }
    }

    /** Inserts or updates the draft's task; its id, or null when the write failed. */
    private suspend fun persistDraftTask(draft: TaskDraft): Long? {
        val description = draft.description.ifBlank { null }
        val editing = _editingTask.value
        if (editing == null) {
            val task = Task.new(
                title = draft.title,
                description = description,
                priority = draft.priority,
            )
            return tasksRepository.insertTask(task)
                .onSuccess { id -> _editingTask.value = task.copy(id = id) }
                .onFailure { _operationFailures.emit(TaskOperationFailure.CREATE) }
                .getOrNull()
        }
        val updated = editing.copy(
            title = draft.title,
            description = description,
            priority = draft.priority,
        )
        val saved = updated == editing || tasksRepository.updateTask(updated).isSuccess
        if (saved) _editingTask.value = updated else _operationFailures.emit(TaskOperationFailure.UPDATE)
        return editing.id.takeIf { saved }
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
            // Read before the delete cascades to them, so Undo can bring them back.
            val subtasks = tasksRepository.getSubtasks(task.id).getOrDefault(emptyList())
            if (tasksRepository.deleteTaskById(task.id).isSuccess) {
                deletedSubtasks[task.id] = subtasks
                hideAddTaskDialog()
                _deletedTasks.emit(task)
            } else {
                _operationFailures.emit(TaskOperationFailure.DELETE)
            }
        }
    }

    /** Undo for [deleteTask]: puts the same task (id, uid, dates) back, with its sub-tasks. */
    fun restoreTask(task: Task) {
        viewModelScope.launch {
            val subtasks = deletedSubtasks[task.id].orEmpty()
            if (tasksRepository.restoreTask(task, subtasks).isSuccess) {
                deletedSubtasks.remove(task.id)
            } else {
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
