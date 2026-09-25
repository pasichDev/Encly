package com.pasich.encly.ui.screens

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.pasich.encly.R
import com.pasich.encly.data.model.Task
import com.pasich.encly.presentation.screen.TasksScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TasksScreenTest : ComposeScreenTest() {
    private val app by lazy { TestApp(context) }

    private fun show(openNewTask: Boolean = false) {
        setNavScreen(viewModels(app.tasksVm()), route = TestRoutes.TASKS) { nav ->
            TasksScreen(nav, openNewTask = openNewTask)
        }
    }

    @Test
    fun noTasksShowsTheEmptyStateWithAnAction() {
        show()

        waitForText(str(R.string.task_empty_title))
        rule.onNodeWithText(str(R.string.task_empty_desc)).assertIsDisplayed()
        assertBodyNotEmpty(minTexts = 3)
    }

    @Test
    fun oneOpenTaskIsListedWithItsPriority() {
        app.withTasks(Task(id = 1, title = "Buy milk", priority = 2))
        show()

        waitForText("Buy milk")
        assertTrue(countText(str(R.string.priority_high)) >= 1)
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
        scrollToText(str(R.string.task_done_section, 1), ignoreCase = true)
        rule.onNodeWithText("Finished").assertIsDisplayed()
        assertBodyNotEmpty(minTexts = 6)
    }

    @Test
    fun allOpenTasksDoneSaysSo() {
        app.withTasks(Task(id = 1, title = "Finished", isCompleted = true, completedDate = 1L))
        show()

        waitForText(str(R.string.task_all_done_title))
        rule.onNodeWithText("Finished").assertIsDisplayed()
    }

    @Test
    fun aNewTaskIsAddedFromTheSheet() {
        show()
        waitForText(str(R.string.task_empty_title))

        rule.onAllNodesWithText(str(R.string.task_add)).onFirst().performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.add)).assertIsNotEnabled()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Water the plants")
        rule.onNodeWithText(str(R.string.add)).performClick()

        waitFor { app.tasks.tasks.value.any { it.title == "Water the plants" } }
        waitForText("Water the plants")
        assertTrue(rule.onAllNodesWithText(str(R.string.add)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun theHomeCardsNewTaskLinkOpensTheSheetOnArrival() {
        show(openNewTask = true)

        waitForText(str(R.string.priority_select_title), ignoreCase = true)
        assertTrue(rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun tickingATaskCompletesIt() {
        app.withTasks(Task(id = 1, title = "Call mum"))
        show()
        waitForText("Call mum")

        rule.onNode(isTaskCheckbox).performClick()

        waitFor { app.tasks.tasks.value.single().isCompleted }
        waitForText(str(R.string.task_done_section, 1), ignoreCase = true)
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

        rule.onNodeWithText("Call mum").performClick()
        waitForText(str(R.string.task_delete))
        rule.onNodeWithText(str(R.string.task_delete)).performClick()

        waitForText(str(R.string.task_deleted))
        assertTrue(app.tasks.tasks.value.isEmpty())
        rule.onNodeWithText(str(R.string.undo)).performClick()
        waitFor { app.tasks.tasks.value.singleOrNull()?.title == "Call mum" }
    }

    private companion object {
        /** A task's own checkbox; the filter chips are checkboxes too, but with a label. */
        val isTaskCheckbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox) and
            SemanticsMatcher.keyNotDefined(SemanticsProperties.Text)
    }
}
