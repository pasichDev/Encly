package com.pasich.encly.presentation.screen

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import com.pasich.encly.core.common.asString
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.clear
import com.pasich.encly.core.resources.task_add
import com.pasich.encly.core.resources.task_all_done_title
import com.pasich.encly.core.resources.task_done_section
import com.pasich.encly.core.resources.task_empty_active
import com.pasich.encly.core.resources.task_empty_desc
import com.pasich.encly.core.resources.task_empty_filtered
import com.pasich.encly.core.resources.task_empty_title
import com.pasich.encly.core.resources.task_section_in_work
import com.pasich.encly.core.resources.task_section_next
import com.pasich.encly.data.model.Subtask
import com.pasich.encly.data.model.Task
import com.pasich.encly.presentation.components.tasks.AddStepRow
import com.pasich.encly.presentation.components.tasks.InlineStepActions
import com.pasich.encly.presentation.components.tasks.StepActions
import com.pasich.encly.presentation.components.tasks.StepChangeActions
import com.pasich.encly.presentation.components.tasks.StepLabels
import com.pasich.encly.presentation.components.tasks.StepNeighbours
import com.pasich.encly.presentation.components.tasks.StepRow
import com.pasich.encly.presentation.components.tasks.TaskFilterChips
import com.pasich.encly.presentation.components.tasks.TaskProgressCard
import com.pasich.encly.presentation.components.tasks.rememberStepLabels
import com.pasich.encly.presentation.components.tiles.TaskItem
import com.pasich.encly.presentation.components.tiles.TaskItemActions
import com.pasich.encly.presentation.designsystem.EnclyEmptyState
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyInlineLink
import com.pasich.encly.presentation.designsystem.EnclyScrollbar
import com.pasich.encly.presentation.designsystem.EnclyTaskSection
import com.pasich.encly.presentation.designsystem.RowSkeleton
import com.pasich.encly.presentation.viewmodel.InlineSubtaskEdit
import com.pasich.encly.presentation.viewmodel.ListLoadErrors
import com.pasich.encly.presentation.viewmodel.TaskFilter
import com.pasich.encly.presentation.viewmodel.TasksUiState
import com.pasich.encly.presentation.viewmodel.TasksViewModel
import com.pasich.encly.ui.theme.EnclyMotion
import com.pasich.encly.ui.theme.EnclyTheme
import org.jetbrains.compose.resources.stringResource

private const val SKELETON_ROWS = 4

// One of each for the whole list: every item would otherwise build its own on every composition.
private val ItemFadeIn: FiniteAnimationSpec<Float> = EnclyMotion.medium()
private val ItemMove: FiniteAnimationSpec<IntOffset> = EnclyMotion.long()
private val ItemFadeOut: FiniteAnimationSpec<Float> = EnclyMotion.short()

/**
 * What the list shows: the tasks, the tasks whose steps the user opened or folded ([stepsChosen]; the others
 * follow their section), and the inline step field.
 */
internal class TasksListState(
    val ui: TasksUiState,
    val stepsChosen: Map<Long, Boolean>,
    val inlineEdit: InlineSubtaskEdit?,
)

/** The list's task and step callbacks, all into [viewModel]. */
internal fun taskItemActions(viewModel: TasksViewModel) = TaskItemActions(
    onToggleTask = viewModel::toggleTaskCompletion,
    onEdit = viewModel::showEditTaskDialog,
    steps = StepActions(
        onToggle = viewModel::toggleSubtask,
        onStartAdding = viewModel::startAddingSubtask,
        onSetOpen = viewModel::setStepsOpen,
        change = StepChangeActions(
            onStartRenaming = viewModel::startRenamingSubtask,
            onMove = { taskId, movedId, targetId -> viewModel.moveSubtask(taskId, movedId, targetId) },
            onDelete = viewModel::deleteSubtask,
        ),
        inline = InlineStepActions(
            onTextChange = viewModel::onInlineTextChange,
            onSubmit = viewModel::submitInlineEdit,
            onClose = viewModel::closeInlineEdit,
        ),
    ),
)

/** The list's own callbacks besides the tasks'. */
internal class TasksListCallbacks(
    val onFilter: (TaskFilter) -> Unit,
    val onNewTask: () -> Unit,
    val onClearCompleted: () -> Unit,
)

/**
 * The tasks a list shows, by state, each in the order the list gets them: [inWork] are open tasks with at
 * least one step done, [next] the other open tasks, [done] the completed ones.
 */
internal class TaskSections(val inWork: List<Task>, val next: List<Task>, val done: List<Task>)

/**
 * The sections of [ui] under its filters. A priority filter narrows the open tasks only (the Done section
 * belongs to "All tasks"); the "Done" filter shows the Done section alone. A task changes section only when
 * its state does: ticking its first step takes it to "in work", completing it to "done".
 */
internal fun taskSections(ui: TasksUiState): TaskSections {
    val showingCompleted = ui.selectedCompletedFilter != null
    val open = if (showingCompleted) emptyList() else ui.filteredActiveTasks
    val done = if (showingCompleted || ui.selectedPriorityFilter == null) ui.completedTasks else emptyList()
    val (inWork, next) = open.partition { task -> ui.subtasks[task.id].orEmpty().any(Subtask::isCompleted) }
    return TaskSections(inWork = inWork, next = next, done = done)
}

/**
 * The tasks list: the progress line and the filters, then the rail with its sections. A tap on nothing in
 * particular closes (and saves) the inline step field.
 */
@Composable
internal fun TasksList(
    state: LazyListState,
    list: TasksListState,
    actions: TaskItemActions,
    callbacks: TasksListCallbacks,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val labels = rememberStepLabels()
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(focusManager) { detectTapGestures { focusManager.clearFocus() } },
            contentPadding = PaddingValues(
                top = EnclyTheme.spacing.xs,
                bottom = EnclyTheme.spacing.fabHeight + EnclyTheme.spacing.l + EnclyTheme.spacing.m,
            ),
        ) {
            tasksContent(list, RowParts(actions, labels), callbacks)
        }
        EnclyScrollbar(state)
    }
}

/** What stands in for the list while it is loading, failed or empty; false once there are tasks to show. */
private fun LazyListScope.tasksPlaceholder(ui: TasksUiState, callbacks: TasksListCallbacks): Boolean {
    when {
        ui.isLoading -> items(SKELETON_ROWS) {
            RowSkeleton(modifier = Modifier.padding(horizontal = EnclyTheme.spacing.gutter))
        }

        ui.loadFailed -> item(key = "failed") {
            EnclyEmptyState(
                icon = EnclyIcons.Checklist,
                title = ListLoadErrors.TASKS.title.asString(),
                body = ListLoadErrors.TASKS.message.asString(),
                error = true,
                modifier = Modifier.padding(horizontal = EnclyTheme.spacing.gutter),
            )
        }

        ui.totalTasksCount == 0 -> item {
            EnclyEmptyState(
                icon = EnclyIcons.Checklist,
                title = stringResource(Res.string.task_empty_title),
                body = stringResource(Res.string.task_empty_desc),
                actionLabel = stringResource(Res.string.task_add),
                onAction = callbacks.onNewTask,
                modifier = Modifier.padding(horizontal = EnclyTheme.spacing.gutter),
            )
        }

        else -> return false
    }
    return true
}

/** The list: a skeleton while loading, else progress, chips and the rail's sections. */
private fun LazyListScope.tasksContent(list: TasksListState, parts: RowParts, callbacks: TasksListCallbacks) {
    val ui = list.ui
    if (tasksPlaceholder(ui, callbacks)) return
    progressAndFilters(ui, callbacks.onFilter)
    val sections = taskSections(ui)
    if (ui.selectedCompletedFilter == null && sections.inWork.isEmpty() && sections.next.isEmpty()) {
        item(key = "empty") {
            EnclyEmptyState(
                icon = EnclyIcons.Checklist,
                title = stringResource(Res.string.task_all_done_title).takeIf { ui.selectedPriorityFilter == null },
                body = stringResource(
                    if (ui.selectedPriorityFilter !=
                        null
                    ) {
                        Res.string.task_empty_filtered
                    } else {
                        Res.string.task_empty_active
                    },
                ),
                modifier = Modifier.padding(horizontal = EnclyTheme.spacing.gutter).animateItem(),
            )
        }
    }
    taskSections(sections, list, parts, callbacks.onClearCompleted)
}

/** What every row of the list is built from: the callbacks and the words, made once. */
private class RowParts(val actions: TaskItemActions, val labels: StepLabels)

/**
 * The sections: "In progress", "Up next" and "Done" with its Clear; one without tasks is not shown. A
 * task's row and each of its steps are items of their own, keyed by their ids, so each moves on the
 * shared curve as tasks change section and steps change place.
 */
private fun LazyListScope.taskSections(
    sections: TaskSections,
    list: TasksListState,
    parts: RowParts,
    onClearCompleted: () -> Unit,
) {
    fun header(key: String, title: @Composable () -> String, onClear: (() -> Unit)? = null) = item(key = key) {
        EnclyTaskSection(
            title = title(),
            action = onClear?.let { clear ->
                { EnclyInlineLink(text = stringResource(Res.string.clear), onClick = clear) }
            },
            modifier = Modifier.listItem(this),
        )
    }
    fun open(tasks: List<Task>, openByDefault: Boolean) = tasks.forEach { task ->
        val stepsOpen = list.stepsChosen[task.id] ?: openByDefault
        item(key = task.id) {
            TaskItem(
                task = task,
                subtasks = list.ui.subtasks[task.id].orEmpty(),
                actions = parts.actions,
                stepsOpen = stepsOpen,
                modifier = Modifier.listItem(this),
            )
        }
        steps(task.id, stepsOpen, list, parts)
    }
    if (sections.inWork.isNotEmpty()) {
        val count = sections.inWork.size
        header("section-in-work", title = { stringResource(Res.string.task_section_in_work, count) })
        open(sections.inWork, openByDefault = true)
    }
    if (sections.next.isNotEmpty()) {
        val count = sections.next.size
        header("section-next", title = { stringResource(Res.string.task_section_next, count) })
        open(sections.next, openByDefault = false)
    }
    if (sections.done.isNotEmpty()) {
        val count = sections.done.size
        header(
            key = "section-done",
            title = { stringResource(Res.string.task_done_section, count) },
            onClear = onClearCompleted,
        )
        items(sections.done, key = { it.id }) { task ->
            TaskItem(
                task = task,
                subtasks = list.ui.subtasks[task.id].orEmpty(),
                actions = parts.actions,
                modifier = Modifier.listItem(this),
            )
        }
    }
}

private fun stepKey(step: Subtask) = "step-${step.id}"

/**
 * The steps of an open task. Open ([stepsOpen]): all of them, then "+ Step". Folded: only the next one
 * to do, so the task's next move is still one tap away (its row's count button opens the rest).
 */
private fun LazyListScope.steps(taskId: Long, stepsOpen: Boolean, list: TasksListState, parts: RowParts) {
    val steps = list.ui.subtasks[taskId].orEmpty()
    if (steps.isEmpty()) return
    val actions = parts.actions.steps
    // One call for both ways of showing them: a step that stays (the next one, as the rest open around it)
    // keeps its composition, and with it a field that is open in it.
    val shown = if (stepsOpen) steps else listOfNotNull(steps.firstOrNull { !it.isCompleted })
    itemsIndexed(shown, key = { _, step -> stepKey(step) }) { index, step ->
        StepRow(
            step = step,
            edit = list.inlineEdit,
            actions = actions,
            labels = parts.labels,
            neighbours = if (stepsOpen) {
                StepNeighbours(above = steps.getOrNull(index - 1), below = steps.getOrNull(index + 1))
            } else {
                StepNeighbours.None
            },
            modifier = Modifier.listItem(this),
        )
    }
    if (stepsOpen) {
        item(key = "add-$taskId") {
            AddStepRow(taskId, list.inlineEdit, actions, parts.labels, Modifier.listItem(this))
        }
    }
}

/** An item of the list as it comes, goes and moves: on the shared curve, with no bounce. */
private fun Modifier.listItem(scope: LazyItemScope): Modifier = with(scope) {
    animateItem(fadeInSpec = ItemFadeIn, placementSpec = ItemMove, fadeOutSpec = ItemFadeOut)
}

private fun LazyListScope.progressAndFilters(state: TasksUiState, onFilter: (TaskFilter) -> Unit) {
    item(key = "progress") {
        // A clear gap under the filter chips, so the rail does not start right against them.
        Column(
            verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s),
            modifier = Modifier
                .padding(horizontal = EnclyTheme.spacing.gutter)
                .padding(bottom = EnclyTheme.spacing.factGap),
        ) {
            TaskProgressCard(
                completionPercentage = state.completionPercentage,
                completedTasksCount = state.completedTasksCount,
                totalTasksCount = state.totalTasksCount,
            )
            TaskFilterChips(
                availableFilters = state.availableFilters,
                selectedFilterIds = setOfNotNull(
                    state.selectedActiveFilter?.id,
                    state.selectedPriorityFilter?.id,
                    state.selectedCompletedFilter?.id,
                ),
                onFilterSelect = onFilter,
            )
        }
    }
}
