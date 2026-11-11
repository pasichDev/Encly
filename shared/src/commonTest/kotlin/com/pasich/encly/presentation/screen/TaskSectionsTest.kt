package com.pasich.encly.presentation.screen

import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.task_add
import com.pasich.encly.data.model.Subtask
import com.pasich.encly.data.model.Task
import com.pasich.encly.presentation.viewmodel.TaskFilter
import com.pasich.encly.presentation.viewmodel.TasksUiState
import kotlin.test.Test
import kotlin.test.assertEquals

/** Which section of the tasks rail a task is in, and what the filters leave of each. */
class TaskSectionsTest {
    private val plain = Task(id = 1, title = "Plain")
    private val planned = Task(id = 2, title = "Planned")
    private val started = Task(id = 3, title = "Started")
    private val allStepsDone = Task(id = 4, title = "All steps done")
    private val finished = Task(id = 9, title = "Finished", isCompleted = true, completedDate = 2L)
    private val finishedEarlier = Task(id = 8, title = "Finished earlier", isCompleted = true, completedDate = 1L)

    private val subtasks = mapOf(
        planned.id to listOf(Subtask(id = 20, taskId = planned.id, title = "a")),
        started.id to listOf(
            Subtask(id = 30, taskId = started.id, title = "a", isCompleted = true),
            Subtask(id = 31, taskId = started.id, title = "b"),
        ),
        allStepsDone.id to listOf(Subtask(id = 40, taskId = allStepsDone.id, title = "a", isCompleted = true)),
        finished.id to listOf(Subtask(id = 90, taskId = finished.id, title = "a", isCompleted = true)),
    )

    private fun ui(
        open: List<Task> = listOf(plain, started, planned, allStepsDone),
        priority: TaskFilter? = null,
        completed: TaskFilter? = null,
    ) = TasksUiState(
        activeTasks = listOf(plain, started, planned, allStepsDone),
        completedTasks = listOf(finished, finishedEarlier),
        filteredActiveTasks = open,
        subtasks = subtasks,
        selectedPriorityFilter = priority,
        selectedCompletedFilter = completed,
        isLoading = false,
    )

    private fun filter(type: TaskFilter.Type) = TaskFilter(id = type.name, label = Res.string.task_add, type = type)

    private fun ids(tasks: List<Task>) = tasks.map { it.id }

    @Test
    fun anOpenTaskWithADoneStepIsInWorkTheOtherOpenOnesAreNextAndCompletedOnesAreDone() {
        val sections = taskSections(ui())

        assertEquals(listOf(started.id, allStepsDone.id), ids(sections.inWork))
        assertEquals(listOf(plain.id, planned.id), ids(sections.next))
        assertEquals(listOf(finished.id, finishedEarlier.id), ids(sections.done))
    }

    @Test
    fun eachSectionKeepsTheOrderTheListCameIn() {
        val sections = taskSections(ui(open = listOf(allStepsDone, planned, started, plain)))

        assertEquals(listOf(allStepsDone.id, started.id), ids(sections.inWork))
        assertEquals(listOf(planned.id, plain.id), ids(sections.next))
    }

    @Test
    fun aCompletedTaskIsDoneWhateverItsSteps() {
        val sections = taskSections(ui())

        assertEquals(true, finished.id in ids(sections.done))
        assertEquals(false, finished.id in ids(sections.inWork))
    }

    @Test
    fun aPriorityFilterNarrowsBothOpenSectionsAndHidesDone() {
        val sections = taskSections(ui(open = listOf(started, planned), priority = filter(TaskFilter.Type.PRIORITY)))

        assertEquals(listOf(started.id), ids(sections.inWork))
        assertEquals(listOf(planned.id), ids(sections.next))
        assertEquals(emptyList(), sections.done)
    }

    @Test
    fun theDoneFilterShowsTheDoneSectionAlone() {
        val sections = taskSections(ui(completed = filter(TaskFilter.Type.COMPLETED)))

        assertEquals(emptyList(), sections.inWork)
        assertEquals(emptyList(), sections.next)
        assertEquals(listOf(finished.id, finishedEarlier.id), ids(sections.done))
    }

    @Test
    fun tickingAFirstStepMovesATaskFromNextToInWork() {
        val ticked =
            subtasks + (planned.id to listOf(Subtask(id = 20, taskId = planned.id, title = "a", isCompleted = true)))

        val sections = taskSections(ui().copy(subtasks = ticked))

        assertEquals(listOf(started.id, planned.id, allStepsDone.id), ids(sections.inWork))
        assertEquals(listOf(plain.id), ids(sections.next))
    }
}
