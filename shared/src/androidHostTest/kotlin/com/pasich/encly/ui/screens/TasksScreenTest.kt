package com.pasich.encly.ui.screens

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.add
import com.pasich.encly.core.resources.priority_high
import com.pasich.encly.core.resources.priority_select_title
import com.pasich.encly.core.resources.save
import com.pasich.encly.core.resources.subtask_add
import com.pasich.encly.core.resources.subtask_first_step
import com.pasich.encly.core.resources.subtask_next_step
import com.pasich.encly.core.resources.subtask_placeholder
import com.pasich.encly.core.resources.subtasks_all_done
import com.pasich.encly.core.resources.subtasks_done_summary
import com.pasich.encly.core.resources.subtasks_hide
import com.pasich.encly.core.resources.subtasks_show
import com.pasich.encly.core.resources.subtasks_state_shown
import com.pasich.encly.core.resources.tag_drag_handle
import com.pasich.encly.core.resources.tag_move_down
import com.pasich.encly.core.resources.tag_move_up
import com.pasich.encly.core.resources.task_add
import com.pasich.encly.core.resources.task_all_done_title
import com.pasich.encly.core.resources.task_delete
import com.pasich.encly.core.resources.task_deleted
import com.pasich.encly.core.resources.task_done_section
import com.pasich.encly.core.resources.task_edit_placeholder
import com.pasich.encly.core.resources.task_empty_desc
import com.pasich.encly.core.resources.task_empty_title
import com.pasich.encly.core.resources.undo
import com.pasich.encly.data.model.Subtask
import com.pasich.encly.data.model.Task
import com.pasich.encly.presentation.screen.TasksScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TasksScreenTest : ComposeScreenTest() {
    private val app by lazy { TestApp() }

    private lateinit var back: OnBackPressedDispatcher

    private fun show(openNewTask: Boolean = false) {
        setNavScreen(viewModels(app.tasksVm()), route = TestRoutes.TASKS) { nav ->
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            TasksScreen(nav, openNewTask = openNewTask)
        }
    }

    private fun pressBack() {
        rule.runOnIdle { back.onBackPressed() }
        rule.waitForIdle()
    }

    @Test
    fun noTasksShowsTheEmptyStateWithAnAction() {
        show()

        waitForText(str(Res.string.task_empty_title))
        rule.onNodeWithText(str(Res.string.task_empty_desc)).assertIsDisplayed()
        assertBodyNotEmpty(minTexts = 3)
    }

    @Test
    fun oneOpenTaskIsListedWithItsPriority() {
        app.withTasks(Task(id = 1, title = "Buy milk", priority = 2))
        show()

        waitForText("Buy milk")
        assertTrue(countText(str(Res.string.priority_high)) >= 1)
        assertBodyNotEmpty()
    }

    @Test
    fun manyTasksShowOpenOnesAndTheDoneSection() {
        app.withTasks(
            *Array(6) { Task(id = it + 1L, title = "Open $it") },
            Task(id = 20, title = "Finished", isCompleted = true, completedDate = 1_700_000_000_000),
        )
        show()

        waitForText("Open 0")
        scrollToText(str(Res.string.task_done_section, 1), ignoreCase = true)
        rule.onNodeWithText("Finished").assertIsDisplayed()
        assertBodyNotEmpty(minTexts = 6)
    }

    @Test
    fun allOpenTasksDoneSaysSo() {
        app.withTasks(Task(id = 1, title = "Finished", isCompleted = true, completedDate = 1L))
        show()

        waitForText(str(Res.string.task_all_done_title))
        rule.onNodeWithText("Finished").assertIsDisplayed()
    }

    @Test
    fun aNewTaskIsAddedFromTheSheet() {
        show()
        waitForText(str(Res.string.task_empty_title))

        rule.onAllNodesWithText(str(Res.string.task_add)).onFirst().performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(Res.string.add)).assertIsNotEnabled()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Water the plants")
        rule.onNodeWithText(str(Res.string.add)).performClick()

        waitFor { app.tasks.tasks.value.any { it.title == "Water the plants" } }
        waitForText("Water the plants")
        assertTrue(rule.onAllNodesWithText(str(Res.string.add)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun theHomeCardsNewTaskLinkOpensTheSheetOnArrival() {
        show(openNewTask = true)

        waitForText(str(Res.string.priority_select_title), ignoreCase = true)
        assertTrue(rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun tickingATaskCompletesIt() {
        app.withTasks(Task(id = 1, title = "Call mum"))
        show()
        waitForText("Call mum")

        rule.onNode(isTaskCheckbox).performClick()

        waitFor { app.tasks.tasks.value.single().isCompleted }
        waitForText(str(Res.string.task_done_section, 1), ignoreCase = true)
        assertEquals(
            ToggleableState.On,
            rule.onNode(isTaskCheckbox).fetchSemanticsNode().config[SemanticsProperties.ToggleableState],
        )
    }

    @Test
    fun aDeletedTaskCanBeBroughtBackFromTheSnackbar() {
        app.withTasks(Task(id = 1, title = "Call mum"))
        show()
        waitForText("Call mum")

        rule.onNodeWithContentDescription(str(Res.string.task_edit_placeholder)).performClick()
        waitForText(str(Res.string.task_delete))
        rule.onNodeWithText(str(Res.string.task_delete)).performClick()

        waitForText(str(Res.string.task_deleted))
        assertTrue(app.tasks.tasks.value.isEmpty())
        rule.onNodeWithText(str(Res.string.undo)).performClick()
        waitFor { app.tasks.tasks.value.singleOrNull()?.title == "Call mum" }
    }

    @Test
    fun foldedATaskShowsOnlyItsNextStepAndTickingItMovesOn() {
        withShoppingList(Subtask(id = 12, taskId = 1, title = "Bread", position = 2))
        show()

        waitForText("Shop")
        // The first open one, not the done Milk or the later Bread; and no "1/3" text anywhere.
        waitForText("Eggs")
        assertEquals(0, countText("Milk"))
        assertEquals(0, countText("Bread"))
        assertEquals(0, countText("/3", substring = true))
        assertEquals(0, countText(str(Res.string.subtask_placeholder)))

        rule.onNodeWithContentDescription(str(Res.string.subtask_next_step, "Eggs")).performClick()

        waitFor { app.tasks.subtasks.value.single { it.id == 11L }.isCompleted }
        waitForText("Bread")
        waitFor { countText("Eggs") == 0 }
        // Saved in place: no sheet opened, and no completion offer while one is still open.
        assertTrue(rule.onAllNodesWithText(str(Res.string.save)).fetchSemanticsNodes().isEmpty())
        assertEquals(0, countText(str(Res.string.subtasks_all_done)))
    }

    @Test
    fun tickingTheLastOpenStepKeepsItStruckAndOffersToComplete() {
        withShoppingList()
        show()
        waitForText("Eggs")

        rule.onNodeWithContentDescription(str(Res.string.subtask_next_step, "Eggs")).performClick()

        waitFor { app.tasks.subtasks.value.all { it.isCompleted } }
        waitForText(str(Res.string.subtasks_all_done))
        rule.onNodeWithText("Eggs").assertIsDisplayed()
        assertEquals(0, countText("Milk"))
        assertTrue(!app.tasks.tasks.value.single().isCompleted)
    }

    @Test
    fun aTaskWithoutSubtasksHasNoLeafAndOpensToFirstStep() {
        app.withTasks(Task(id = 1, title = "Call mum"))
        show()
        waitForText("Call mum")
        assertEquals(0, countText(str(Res.string.subtask_first_step)))
        assertEquals(1, rule.onAllNodes(isTaskCheckbox).fetchSemanticsNodes().size)

        rule.onNodeWithText("Call mum").performClick()

        waitForText(str(Res.string.subtask_first_step))
        // The task row and the add leaf: no sub-task checkboxes.
        assertEquals(1, rule.onAllNodes(isTaskCheckbox).fetchSemanticsNodes().size)
    }

    @Test
    fun aTapOnTheRowShowsTheWholeTreeAndBackFoldsIt() {
        withShoppingList(Subtask(id = 12, taskId = 1, title = "Bread", position = 2))
        show()
        waitForText("Eggs")

        rule.onNodeWithText("Shop").performClick()

        waitForText("Milk")
        rule.onNodeWithText("Bread").assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.subtask_placeholder)).assertIsDisplayed()

        pressBack()

        waitFor { countText("Milk") == 0 }
        rule.onNodeWithText("Eggs").assertIsDisplayed()
        // The screen is still there.
        rule.onNodeWithText("Shop").assertIsDisplayed()
    }

    @Test
    fun theLeafsChevronOpensTheTree() {
        withShoppingList()
        show()
        waitForText("Eggs")

        rule.onNodeWithContentDescription(str(Res.string.subtasks_show)).performClick()

        waitForText("Milk")
        rule.onNodeWithContentDescription(str(Res.string.subtasks_hide)).performClick()
        waitFor { countText("Milk") == 0 }
    }

    @Test
    fun theRowReadsItsProgressAndOffersActions() {
        withShoppingList()
        show()
        waitForText("Shop")
        val row = rule.onNodeWithText("Shop")
        val config = row.fetchSemanticsNode().config

        val summary = plural(Res.plurals.subtasks_done_summary, 2, 1, 2)
        assertTrue(config[SemanticsProperties.StateDescription].startsWith(summary))
        assertEquals(str(Res.string.subtasks_show), config[SemanticsActions.OnClick].label)
        assertEquals(
            listOf(str(Res.string.subtasks_show), str(Res.string.task_edit_placeholder), str(Res.string.subtask_add)),
            config[SemanticsActions.CustomActions].map { it.label },
        )
        row.performClick()
        waitFor {
            row.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
                ?.endsWith(str(Res.string.subtasks_state_shown)) == true
        }
        assertEquals(str(Res.string.subtasks_hide), row.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
    }

    @Test
    fun theFirstStepTurnsIntoAFieldThatAddsOnDone() {
        app.withTasks(Task(id = 1, title = "Call mum"))
        show()
        waitForText("Call mum")
        rule.onNodeWithText("Call mum").performClick()
        waitForText(str(Res.string.subtask_first_step))

        rule.onNodeWithText(str(Res.string.subtask_first_step)).performClick()
        rule.waitForIdle()
        val field = rule.onNode(hasSetTextAction())
        field.assertIsFocused()
        field.performTextInput("Birthday")
        field.performImeAction()

        waitFor { app.tasks.subtasks.value.singleOrNull()?.title == "Birthday" }
        waitForText("Birthday")
        // Still open, empty and focused, for the next one; the FAB is out of the way meanwhile.
        rule.onNode(hasSetTextAction()).assertIsFocused()
        assertEquals(0, countText(str(Res.string.task_add)))

        // Back closes the field first, and only then folds the tree.
        pressBack()
        waitFor { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText(str(Res.string.subtask_placeholder)).assertIsDisplayed()
        pressBack()
        waitFor { countText(str(Res.string.subtask_placeholder)) == 0 }
        // Folded, it now shows its next step like any task with sub-tasks.
        rule.onNodeWithText("Birthday").assertIsDisplayed()
    }

    @Test
    fun aDoneTaskShowsNoNextStepUntilOpened() {
        app.withTasks(Task(id = 1, title = "Shop", isCompleted = true, completedDate = 1L))
        app.tasks.subtasks.value = listOf(Subtask(id = 11, taskId = 1, title = "Eggs", position = 0))
        show()
        waitForText("Shop")
        assertEquals(0, countText("Eggs"))

        rule.onNodeWithText("Shop").performClick()

        waitForText("Eggs")
        rule.onNodeWithContentDescription("Eggs").performClick()
        waitFor { app.tasks.subtasks.value.single().isCompleted }
    }

    @Test
    fun theEditButtonOpensTheSheet() {
        withShoppingList()
        show()
        waitForText("Shop")

        rule.onNodeWithContentDescription(str(Res.string.task_edit_placeholder)).performClick()

        waitForText(str(Res.string.task_delete))
        // The sheet with the task's checklist.
        waitForText("Milk")
    }

    @Test
    fun moveUpAndDownAreOnTheTitleTalkBackFocusesInTheTree() {
        withShoppingList()
        show()
        waitForText("Shop")
        rule.onNodeWithText("Shop").performClick()
        waitForText("Milk")

        val milk = rule.onNode(hasText("Milk") and hasClickAction())
        val eggs = rule.onNode(hasText("Eggs") and hasClickAction())
        assertEquals(listOf(str(Res.string.tag_move_down)), customActions(milk))
        assertEquals(listOf(str(Res.string.tag_move_up)), customActions(eggs))

        // And they move the row.
        rule.runOnIdle {
            milk.fetchSemanticsNode().config[SemanticsActions.CustomActions].single().action()
        }
        waitFor { app.tasks.subtasks.value.sortedBy { it.position }.map { it.title } == listOf("Eggs", "Milk") }
    }

    @Test
    fun moveUpAndDownAreOnTheDragHandlesInTheSheet() {
        withShoppingList()
        show()
        waitForText("Shop")
        rule.onNodeWithContentDescription(str(Res.string.task_edit_placeholder)).performClick()
        waitForText(str(Res.string.task_delete))
        waitForText("Milk")

        val handles = rule.onAllNodesWithContentDescription(str(Res.string.tag_drag_handle))
        assertEquals(listOf(str(Res.string.tag_move_down)), customActions(handles[0]))
        assertEquals(listOf(str(Res.string.tag_move_up)), customActions(handles[1]))
    }

    private fun customActions(node: SemanticsNodeInteraction): List<String> =
        node.fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }

    private fun withShoppingList(vararg more: Subtask) {
        app.withTasks(Task(id = 1, title = "Shop"))
        app.tasks.subtasks.value = listOf(
            Subtask(id = 10, taskId = 1, title = "Milk", isCompleted = true, position = 0),
            Subtask(id = 11, taskId = 1, title = "Eggs", position = 1),
        ) + more
    }

    private companion object {
        /** A task's own checkbox; the filter chips are checkboxes too, but with a label. */
        val isTaskCheckbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox) and
            SemanticsMatcher.keyNotDefined(SemanticsProperties.Text)
    }
}
