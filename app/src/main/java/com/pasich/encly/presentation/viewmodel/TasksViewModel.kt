package com.pasich.encly.presentation.viewmodel

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.security.NeverLocked
import com.pasich.encly.core.security.VaultLockEvents
import com.pasich.encly.data.model.Subtask
import com.pasich.encly.data.model.Task
import com.pasich.encly.domain.repository.TasksRepository
import com.pasich.encly.domain.usecase.task.UpdateTaskStatusUseCase
import com.pasich.encly.presentation.dialogs.tasks.SUBTASK_TITLE_MAX_LENGTH
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

/** SavedStateHandle key of the tasks whose sub-task tree is open. */
private const val KEY_EXPANDED_TASKS = "expandedTaskIds"

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
    /** Sub-tasks by task id, in their order; a task without sub-tasks has no entry. */
    val subtasks: Map<Long, List<Subtask>> = emptyMap(),
    val isLoading: Boolean = true,
)

/**
 * The Tasks screen: the lists and filters, the editor sheet, and the list's own sub-task
 * editing (each task's tree opens on a tap; one inline field adds or renames a sub-task).
 */
@HiltViewModel
class TasksViewModel @Inject constructor(
    private val tasksRepository: TasksRepository,
    private val updateTaskStatusUseCase: UpdateTaskStatusUseCase,
    lockEvents: VaultLockEvents = NeverLocked,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
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

    /**
     * Tasks whose sub-task tree is open on the list (all closed at first). Kept in the saved
     * state, so a rotation keeps them open; ids of tasks that are gone are dropped.
     */
    private val _expandedTaskIds =
        MutableStateFlow(savedStateHandle.get<LongArray>(KEY_EXPANDED_TASKS)?.toSet().orEmpty())
    val expandedTaskIds: StateFlow<Set<Long>> = _expandedTaskIds.asStateFlow()

    /**
     * The one inline sub-task field open on the list, or null. Compose state rather than a flow,
     * so the field reads back what was typed without a frame of delay.
     */
    var inlineEdit: InlineSubtaskEdit? by mutableStateOf(null)
        private set

    /** A sub-task that was just deleted from the list, so the screen can offer to undo it. */
    private val _subtaskDeletions = MutableSharedFlow<Subtask>(extraBufferCapacity = 1)
    val subtaskDeletions: SharedFlow<Subtask> = _subtaskDeletions.asSharedFlow()

    /** Sub-tasks of deleted tasks by task id, for [restoreTask]; the delete cascades to them. */
    private val deletedSubtasks = mutableMapOf<Long, List<Subtask>>()

    private var subtasksJob: Job? = null

    /** Serializes background draft saves so two quick pauses cannot insert twice. */
    private val draftMutex = Mutex()

    /**
     * Serializes the list's sub-task writes (ticks, inline adds, renames, deletes) and the
     * sheet's checklist load after them: two quick ticks cannot both offer to complete the task,
     * and the sheet shows what the list just saved.
     */
    private val subtaskMutex = Mutex()

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
            inlineEdit = null
        }
    }

    private fun observeTasks() {
        tasksJob = viewModelScope.launch {
            combine(
                tasksRepository.getAllActiveTasks(),
                tasksRepository.getAllCompletedTasks(),
                tasksRepository.getActiveTasksCount(),
                tasksRepository.getCompletedTasksCount(),
                tasksRepository.observeSubtasks(),
            ) { activeTasks, completedTasks, activeCount, completedCount, subtasks ->
                TaskFilterEngine.reduce(_uiState.value, activeTasks, completedTasks, activeCount, completedCount)
                    .copy(subtasks = subtasks.groupBy { it.taskId })
            }.collect { newState ->
                _uiState.value = newState
                dropGoneListState(newState)
            }
        }
    }

    /** Forgets open trees and the inline field of tasks (or a renamed sub-task) that are gone. */
    private fun dropGoneListState(state: TasksUiState) {
        val ids = (state.activeTasks + state.completedTasks).mapTo(HashSet()) { it.id }
        val expanded = _expandedTaskIds.value
        if (!ids.containsAll(expanded)) setExpanded(expanded.filterTo(LinkedHashSet()) { it in ids })
        val target = inlineEdit?.target ?: return
        val renamedGone = target is InlineSubtaskTarget.Rename &&
            state.subtasks[target.taskId].orEmpty().none { it.id == target.subtaskId }
        val gone = target.taskId !in ids || renamedGone
        if (gone) inlineEdit = null
    }

    private fun setExpanded(ids: Set<Long>) {
        _expandedTaskIds.value = ids
        savedStateHandle[KEY_EXPANDED_TASKS] = ids.toLongArray()
    }

    /** Opens or closes the task's sub-task tree on the list. The set keeps the order they opened in. */
    fun toggleSubtasks(taskId: Long) {
        commitInlineEdit()
        val expanded = _expandedTaskIds.value
        setExpanded(if (taskId in expanded) expanded - taskId else expanded + taskId)
    }

    /**
     * Back on the list: folds the tree opened last among [visible] (the tasks the list shows
     * now). False when none of them is open, so Back can leave the screen.
     */
    fun collapseLastExpanded(visible: Set<Long>): Boolean {
        val last = _expandedTaskIds.value.lastOrNull { it in visible } ?: return false
        commitInlineEdit()
        setExpanded(_expandedTaskIds.value - last)
        return true
    }

    /** Drag or "Move up/down" in a task's open tree: stores the new order, keeping each row's identity. */
    fun moveSubtask(taskId: Long, from: Int, to: Int) {
        commitInlineEdit()
        launchSubtaskWrite {
            val rows = tasksRepository.getSubtasks(taskId).getOrNull() ?: return@launchSubtaskWrite
            if (from !in rows.indices || to !in rows.indices || from == to) return@launchSubtaskWrite
            val moved = rows.toMutableList().apply { add(to, removeAt(from)) }
            if (tasksRepository.saveSubtasks(
                    taskId,
                    moved,
                ).isFailure
            ) {
                _operationFailures.emit(TaskOperationFailure.UPDATE)
            }
        }
    }

    /** Turns the task's "Add sub-task" button into the inline field (closing any other one). */
    fun startAddingSubtask(taskId: Long) {
        val target = InlineSubtaskTarget.Add(taskId)
        if (inlineEdit?.target == target) return
        commitInlineEdit()
        setExpanded(_expandedTaskIds.value + taskId)
        inlineEdit = InlineSubtaskEdit(target, text = "")
    }

    /** Turns the sub-task's row into the inline field with its title (closing any other one). */
    fun startRenamingSubtask(subtask: Subtask) {
        val target = InlineSubtaskTarget.Rename(subtask.taskId, subtask.id)
        if (inlineEdit?.target == target) return
        commitInlineEdit()
        inlineEdit = InlineSubtaskEdit(target, text = subtask.title, original = subtask)
    }

    fun onInlineTextChange(text: String) {
        inlineEdit = inlineEdit?.copy(text = text.take(SUBTASK_TITLE_MAX_LENGTH))
    }

    /**
     * The keyboard's Done. Adding: a title is saved as the task's last sub-task and the field
     * stays open, empty, for the next one; a blank field closes. Renaming: like [closeInlineEdit].
     */
    fun submitInlineEdit() {
        val edit = inlineEdit ?: return
        if (edit.target is InlineSubtaskTarget.Add && edit.text.isNotBlank()) {
            inlineEdit = edit.copy(text = "")
            writeInlineEdit(edit)
        } else {
            closeInlineEdit(edit.target)
        }
    }

    /**
     * Closes the field for [target] (it lost focus, the user tapped elsewhere or pressed Back)
     * and saves it: a new title is added, a rename is stored, and a rename left blank deletes the
     * sub-task (with Undo). A blank new sub-task is simply dropped. Ignored when the field open
     * now is another one.
     */
    fun closeInlineEdit(target: InlineSubtaskTarget) {
        val edit = inlineEdit?.takeIf { it.target == target } ?: return
        inlineEdit = null
        writeInlineEdit(edit)
    }

    /** The ✕ of a sub-task being renamed: deletes it, with Undo. */
    fun deleteInlineSubtask() {
        val original = inlineEdit?.original ?: return
        inlineEdit = null
        launchSubtaskWrite { deleteSubtaskWithUndo(original) }
    }

    /** Undo for a sub-task deleted from the list: back at its position, with its uid. */
    fun restoreSubtask(subtask: Subtask) {
        launchSubtaskWrite {
            if (tasksRepository.restoreSubtask(subtask).isFailure) _operationFailures.emit(TaskOperationFailure.UPDATE)
        }
    }

    /**
     * Saves what the inline field holds when the app leaves the foreground (the re-lock drops
     * the screen), like the sheet's [saveDraftForBackground]. A blank field saves nothing. The
     * field stays open: an added title is cleared from it so it is not added twice.
     */
    fun flushInlineEditForBackground() {
        val edit = inlineEdit ?: return
        if (edit.text.isBlank()) return
        inlineEdit = when (edit.target) {
            is InlineSubtaskTarget.Add -> edit.copy(text = "")
            is InlineSubtaskTarget.Rename -> edit.copy(original = edit.original?.copy(title = edit.text.trim()))
        }
        writeInlineEdit(edit)
    }

    /**
     * Saves and closes the open inline field, like losing focus does: before any other
     * interaction with the list, and when the screen is left.
     */
    fun commitInlineEdit() {
        inlineEdit?.let { closeInlineEdit(it.target) }
    }

    private fun writeInlineEdit(edit: InlineSubtaskEdit) {
        val title = edit.text.trim()
        val original = edit.original
        launchSubtaskWrite {
            val failed = when {
                original == null -> title.isNotEmpty() &&
                    tasksRepository.addSubtask(edit.target.taskId, title).isFailure

                title.isEmpty() -> !deleteSubtaskWithUndo(original)

                title != original.title -> tasksRepository.renameSubtask(original.id, title).isFailure

                else -> false
            }
            if (failed) _operationFailures.emit(TaskOperationFailure.UPDATE)
        }
    }

    /**
     * Deletes [subtask] and offers Undo; false when the delete failed. Deleting the last open
     * sub-task of an open task leaves only done ones, so it offers to complete the task too,
     * like ticking it would.
     */
    private suspend fun deleteSubtaskWithUndo(subtask: Subtask): Boolean {
        val deleted = tasksRepository.deleteSubtask(subtask.id).isSuccess
        if (deleted) {
            _subtaskDeletions.emit(subtask)
            offerCompletionAfterDelete(subtask)
        }
        return deleted
    }

    private suspend fun offerCompletionAfterDelete(deleted: Subtask) {
        val after = tasksRepository.getSubtasks(deleted.taskId).getOrNull() ?: return
        val state = uiState.value
        val task = (state.activeTasks + state.completedTasks).find { it.id == deleted.taskId }
        if (task != null && SubtaskDrafts.offersCompletion(task.isCompleted, after + deleted, after)) {
            _completionOffers.emit(task.id)
        }
    }

    private fun launchSubtaskWrite(write: suspend () -> Unit) {
        viewModelScope.launch { subtaskMutex.withLock { write() } }
    }

    fun showAddTaskDialog() {
        commitInlineEdit()
        subtasksJob?.cancel()
        _editingTask.value = null
        _editingSubtasks.value = emptyList()
        _showAddTaskDialog.value = true
    }

    fun showEditTaskDialog(task: Task) {
        commitInlineEdit()
        subtasksJob?.cancel()
        _editingTask.value = task
        // Unknown until loaded: a sheet saved before then leaves the stored checklist alone,
        // and one that fails to load never overwrites it with an empty list.
        _editingSubtasks.value = null
        _showAddTaskDialog.value = true
        subtasksJob = viewModelScope.launch {
            // After any list write still in flight, so the sheet shows it.
            subtaskMutex.withLock { tasksRepository.getSubtasks(task.id) }
                .onSuccess { _editingSubtasks.value = SubtaskDrafts.fromSubtasks(it) }
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
        commitInlineEdit()
        viewModelScope.launch {
            if (updateTaskStatusUseCase(taskId, isCompleted).isFailure) {
                _operationFailures.emit(TaskOperationFailure.STATUS_UPDATE)
            }
        }
    }

    /**
     * Ticks or unticks a sub-task from the list, saved at once. When that ticks the last open
     * sub-task of an open task, offers to complete it, like a sheet save does.
     */
    fun toggleSubtask(subtask: Subtask, done: Boolean) {
        commitInlineEdit()
        viewModelScope.launch {
            subtaskMutex.withLock {
                if (tasksRepository.setSubtaskCompleted(subtask.id, done).isFailure) {
                    _operationFailures.emit(TaskOperationFailure.STATUS_UPDATE)
                    return@withLock
                }
                val after = tasksRepository.getSubtasks(subtask.taskId).getOrNull() ?: return@withLock
                val before = after.map { if (it.id == subtask.id) it.copy(isCompleted = !done) else it }
                val state = uiState.value
                val task = (state.activeTasks + state.completedTasks).find { it.id == subtask.taskId }
                if (task != null && SubtaskDrafts.offersCompletion(task.isCompleted, before, after)) {
                    _completionOffers.emit(task.id)
                }
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
        commitInlineEdit()
        _uiState.value = TaskFilterEngine.select(_uiState.value, filter)
    }
}
