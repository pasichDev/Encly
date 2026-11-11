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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.add
import com.pasich.encly.core.resources.back
import com.pasich.encly.core.resources.done
import com.pasich.encly.core.resources.edit
import com.pasich.encly.core.resources.priority_high
import com.pasich.encly.core.resources.priority_select_title
import com.pasich.encly.core.resources.save
import com.pasich.encly.core.resources.step_add
import com.pasich.encly.core.resources.subtask_add
import com.pasich.encly.core.resources.subtask_delete
import com.pasich.encly.core.resources.subtask_deleted
import com.pasich.encly.core.resources.subtask_placeholder
import com.pasich.encly.core.resources.subtasks_all_done
import com.pasich.encly.core.resources.subtasks_done_summary
import com.pasich.encly.core.resources.subtasks_hide
import com.pasich.encly.core.resources.subtasks_show
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
import com.pasich.encly.core.resources.task_section_in_work
import com.pasich.encly.core.resources.task_section_next
import com.pasich.encly.core.resources.undo
import com.pasich.encly.data.model.Subtask
import com.pasich.encly.data.model.Task
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.TasksScreen
import org.jetbrains.compose.resources.StringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TasksScreenTest : ComposeScreenTest() {
    private val app by lazy { TestApp() }

    private fun show(openNewTask: Boolean = false) {
        setNavScreen(viewModels(app.tasksVm()), route = TestRoutes.TASKS) { nav ->
            TasksScreen(nav, openNewTask = openNewTask)
        }
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

        rule.onNodeWithText("Call mum").performClick()
        waitForText(str(Res.string.task_delete))
        rule.onNodeWithText(str(Res.string.task_delete)).performClick()

        waitForText(str(Res.string.task_deleted))
        assertTrue(app.tasks.tasks.value.isEmpty())
        rule.onNodeWithText(str(Res.string.undo)).performClick()
        waitFor { app.tasks.tasks.value.singleOrNull()?.title == "Call mum" }
    }

    /** Where a text sits down the list. */
    private fun top(text: String) =
        rule.onNode(hasText(text, ignoreCase = true), useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y

    @Test
    fun theRailSortsTasksByStateInProgressThenNextThenDone() {
        app.withTasks(
            Task(id = 1, title = "Plain"),
            Task(id = 2, title = "Started"),
            Task(id = 3, title = "Planned"),
            Task(id = 9, title = "Finished", isCompleted = true, completedDate = 1_700_000_000_000),
        )
        app.tasks.subtasks.value = listOf(
            Subtask(id = 20, taskId = 2, title = "First", isCompleted = true, position = 0),
            Subtask(id = 21, taskId = 2, title = "Second", position = 1),
            Subtask(id = 30, taskId = 3, title = "Later", position = 0),
        )
        show()
        waitForText("Started")

        val inWork = str(Res.string.task_section_in_work, 1)
        val next = str(Res.string.task_section_next, 2)
        val done = str(Res.string.task_done_section, 1)
        val order = listOf(inWork, "Started", "First", "Second", next, done, "Finished").map(::top)
        assertEquals("sections and their tasks run down the rail in this order", order.sorted(), order)
        assertTrue("an open task without a done step is under Up next", top("Plain") in top(next)..top(done))
        assertTrue(top("Planned") in top(next)..top(done))
    }

    @Test
    fun tickingATasksFirstStepMovesItToInProgress() {
        app.withTasks(Task(id = 1, title = "Shop"))
        app.tasks.subtasks.value = listOf(Subtask(id = 11, taskId = 1, title = "Eggs", position = 0))
        show()
        waitForText(str(Res.string.task_section_next, 1), ignoreCase = true)
        assertEquals(0, countText(str(Res.string.task_section_in_work, 1), ignoreCase = true))

        rule.onNodeWithContentDescription("Eggs").performClick()

        waitFor { app.tasks.subtasks.value.single().isCompleted }
        waitForText(str(Res.string.task_section_in_work, 1), ignoreCase = true)
        waitFor { countText(str(Res.string.task_section_next, 1), ignoreCase = true) == 0 }
        rule.onNodeWithText("Shop").assertIsDisplayed()
    }

    @Test
    fun tickingTheLastOpenStepKeepsTheTaskOpenAndOffersToCompleteIt() {
        withShoppingList()
        show()
        waitForText("Eggs")

        rule.onNodeWithContentDescription("Eggs").performClick()

        waitFor { app.tasks.subtasks.value.all { it.isCompleted } }
        waitForText(str(Res.string.subtasks_all_done))
        // Still open, still in progress, its steps struck through in place.
        assertTrue(app.tasks.tasks.value.single().isCompleted.not())
        rule.onNodeWithText("Eggs").assertIsDisplayed()
        waitForText(str(Res.string.task_section_in_work, 1), ignoreCase = true)
    }

    @Test
    fun aDoneTaskShowsNoSteps() {
        app.withTasks(Task(id = 1, title = "Shop", isCompleted = true, completedDate = 1L))
        app.tasks.subtasks.value = listOf(Subtask(id = 11, taskId = 1, title = "Eggs", position = 0))
        show()

        waitForText("Shop")
        assertEquals(0, countText("Eggs"))
    }

    @Test
    fun anEditedTasksSheetEndsInDeleteAndSaveAsTwoEqualButtons() {
        app.withTasks(Task(id = 1, title = "Call mum"))
        show()
        waitForText("Call mum")

        rule.onNodeWithText("Call mum").performClick()
        waitForText(str(Res.string.task_delete))

        val delete = rule.onNode(hasText(str(Res.string.task_delete)) and hasClickAction()).getBoundsInRoot()
        val save = rule.onNode(hasText(str(Res.string.save)) and hasClickAction()).getBoundsInRoot()
        // Two halves of the row: a pixel of rounding at most between them.
        assertEquals(delete.width.value, save.width.value, 1f)
        assertEquals(delete.height.value, save.height.value, 0.01f)
        assertEquals(delete.top.value, save.top.value, 0.01f)
        // Delete first, Save after it, a gap between them.
        assertTrue(delete.right < save.left)
    }

    @Test
    fun aNewTasksSheetEndsInAddAlone() {
        show()
        waitForText(str(Res.string.task_empty_title))

        rule.onAllNodesWithText(str(Res.string.task_add)).onFirst().performClick()
        rule.waitForIdle()

        rule.onNode(hasText(str(Res.string.add)) and hasClickAction()).assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText(str(Res.string.task_delete)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aTapOnTheRowOpensTheSheet() {
        withShoppingList()
        show()
        waitForText("Shop")

        rule.onNodeWithText("Shop").performClick()

        waitForText(str(Res.string.task_delete))
        // The sheet with the task's checklist: its steps are there to rename, add to and reorder.
        assertTrue(rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun theRowOffersEditToTalkBack() {
        withShoppingList()
        show()
        waitForText("Shop")

        val row = rule.onNode(hasText("Shop") and hasClickAction())
        assertEquals(
            listOf(str(Res.string.task_edit_placeholder), str(Res.string.subtasks_hide)),
            customActions(row),
        )
    }

    @Test
    fun moveUpAndDownAreOnTheDragHandlesInTheSheet() {
        withShoppingList()
        show()
        waitForText("Shop")
        rule.onNodeWithText("Shop").performClick()
        waitForText(str(Res.string.task_delete))

        val handles = rule.onAllNodesWithContentDescription(str(Res.string.tag_drag_handle))
        assertEquals(listOf(str(Res.string.tag_move_down)), customActions(handles[0]))
        assertEquals(listOf(str(Res.string.tag_move_up)), customActions(handles[1]))
    }

    private fun steps() = app.tasks.subtasks.value.filter { it.taskId == 1L }.sortedBy { it.position }.map { it.title }

    private fun fields() = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size

    private fun rename(step: String) {
        rule.onNodeWithText(step).performTouchInput { longClick() }
        waitFor { fields() == 1 }
    }

    private fun barButton(id: StringResource) = rule.onNode(hasContentDescription(str(id)) and hasClickAction())

    /** A task in progress with [count] steps, the first one done. */
    private fun withSteps(count: Int) {
        app.withTasks(Task(id = 1, title = "Trip"))
        app.tasks.subtasks.value = List(count) {
            Subtask(id = 10L + it, taskId = 1, title = "Step $it", isCompleted = it == 0, position = it)
        }
    }

    @Test
    fun anUpNextTaskShowsOnlyItsNextStepWithACount() {
        app.withTasks(Task(id = 1, title = "Trip"))
        app.tasks.subtasks.value = List(3) { Subtask(id = 10L + it, taskId = 1, title = "Step $it", position = it) }
        show()

        waitForText("Step 0")
        assertEquals(0, countText("Step 1"))
        assertEquals(0, countText("Step 2"))
        rule.onNodeWithText("0 / 3").assertIsDisplayed()
        assertEquals("no + Step outside In progress", 0, countText(str(Res.string.step_add)))

        // Ticking it starts the task: it is in progress, with all its steps.
        rule.onNodeWithContentDescription("Step 0").performClick()
        waitForText(str(Res.string.task_section_in_work, 1), ignoreCase = true)
        waitForText("Step 2")
        rule.onNodeWithText("1 / 3").assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.step_add)).assertExists()
    }

    private fun stepsButton(show: Boolean) = rule.onNode(
        hasContentDescription(str(if (show) Res.string.subtasks_show else Res.string.subtasks_hide)) and
            hasClickAction(),
    )

    @Test
    fun aTaskInProgressShowsAllItsStepsHoweverMany() {
        withSteps(6)
        show()

        waitForText("Step 5")
        rule.onNodeWithText("1 / 6").assertIsDisplayed()
    }

    @Test
    fun theCountButtonOpensTheStepsOfAnUpNextTaskAndFoldsThemAgain() {
        app.withTasks(Task(id = 1, title = "Trip"))
        app.tasks.subtasks.value = List(3) { Subtask(id = 10L + it, taskId = 1, title = "Step $it", position = it) }
        show()
        waitForText("Step 0")
        assertEquals(0, countText("Step 2"))

        stepsButton(show = true).performClick()

        waitForText("Step 2")
        rule.onNodeWithText(str(Res.string.step_add)).assertExists()
        // Still "up next": opening its steps starts nothing.
        waitForText(str(Res.string.task_section_next, 1), ignoreCase = true)

        stepsButton(show = false).performClick()
        waitFor { countText("Step 2") == 0 }
        rule.onNodeWithText("Step 0").assertIsDisplayed()
        assertEquals(0, countText(str(Res.string.step_add)))
    }

    @Test
    fun theCountButtonFoldsATaskInProgressToItsNextOpenStep() {
        withSteps(4)
        show()
        waitForText("Step 3")

        stepsButton(show = false).performClick()

        // "Step 0" is done: the next one to do is "Step 1".
        waitFor { countText("Step 0") == 0 }
        rule.onNodeWithText("Step 1").assertIsDisplayed()
        assertEquals(0, countText("Step 2"))
        assertEquals(0, countText(str(Res.string.step_add)))
        stepsButton(show = true).assertExists()
    }

    @Test
    fun aTaskWithoutStepsAndADoneTaskHaveNoCountButton() {
        app.withTasks(
            Task(id = 1, title = "Plain"),
            Task(id = 2, title = "Finished", isCompleted = true, completedDate = 1L),
        )
        app.tasks.subtasks.value = listOf(Subtask(id = 20, taskId = 2, title = "Old", isCompleted = true, position = 0))
        show()
        waitForText("Plain")

        assertEquals(
            0,
            rule.onAllNodes(hasContentDescription(str(Res.string.subtasks_show))).fetchSemanticsNodes().size,
        )
        assertEquals(
            0,
            rule.onAllNodes(hasContentDescription(str(Res.string.subtasks_hide))).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun plusStepAddsStepsOneAfterAnotherAndABlankOneClosesTheField() {
        withShoppingList()
        show()
        waitForText("Eggs")
        assertEquals(0, fields())

        rule.onNodeWithText(str(Res.string.step_add)).performClick()
        waitFor { fields() == 1 }
        rule.onNode(hasSetTextAction()).assertIsFocused()
        rule.onNode(hasSetTextAction()).performTextInput("Bread")
        rule.onNode(hasSetTextAction()).performImeAction()
        waitFor { steps() == listOf("Milk", "Eggs", "Bread") }

        // Still open and empty for the next one.
        assertEquals(1, fields())
        rule.onNode(hasSetTextAction()).performTextInput("Tea")
        rule.onNode(hasSetTextAction()).performImeAction()
        waitFor { steps() == listOf("Milk", "Eggs", "Bread", "Tea") }

        rule.onNode(hasSetTextAction()).performImeAction()
        waitFor { fields() == 0 }
        assertEquals(listOf("Milk", "Eggs", "Bread", "Tea"), steps())
        rule.onNodeWithText(str(Res.string.step_add)).assertExists()
    }

    @Test
    fun aLongPressRenamesAStepAndDoneSavesIt() {
        withShoppingList()
        show()
        waitForText("Eggs")

        rename("Eggs")
        rule.onNode(hasSetTextAction()).performTextReplacement("Brown eggs")
        rule.onNodeWithText(str(Res.string.done)).performClick()

        waitFor { steps() == listOf("Milk", "Brown eggs") }
        waitFor { fields() == 0 }
        // The bar leaves with the field.
        assertEquals(0, countText(str(Res.string.done)))
    }

    @Test
    fun aRenameLeftEmptyKeepsTheOldTitle() {
        withShoppingList()
        show()
        waitForText("Eggs")

        rename("Eggs")
        rule.onNode(hasSetTextAction()).performTextReplacement("")
        rule.onNodeWithText(str(Res.string.done)).performClick()

        waitFor { fields() == 0 }
        assertEquals(listOf("Milk", "Eggs"), steps())
        rule.onNodeWithText("Eggs").assertIsDisplayed()
    }

    @Test
    fun theBarsArrowsMoveTheStepAndAreOffAtTheEnds() {
        withShoppingList(Subtask(id = 12, taskId = 1, title = "Bread", position = 2))
        show()
        waitForText("Bread")

        rename("Milk")
        barButton(Res.string.tag_move_up).assertIsNotEnabled()
        barButton(Res.string.tag_move_down).assertIsEnabled()

        barButton(Res.string.tag_move_down).performClick()
        waitFor { steps() == listOf("Eggs", "Milk", "Bread") }
        assertEquals("the field stays open on the moved step", 1, fields())
        barButton(Res.string.tag_move_up).assertIsEnabled()

        barButton(Res.string.tag_move_down).performClick()
        waitFor { steps() == listOf("Eggs", "Bread", "Milk") }
        rule.waitForIdle()
        barButton(Res.string.tag_move_down).assertIsNotEnabled()
    }

    @Test
    fun theBarsDeleteRemovesTheStepAndOffersUndo() {
        withShoppingList()
        show()
        waitForText("Eggs")

        rename("Eggs")
        barButton(Res.string.subtask_delete).performClick()

        waitFor { steps() == listOf("Milk") }
        waitForText(str(Res.string.subtask_deleted))
        rule.onNodeWithText(str(Res.string.undo)).performClick()
        waitFor { steps() == listOf("Milk", "Eggs") }
    }

    @Test
    fun onlyOneFieldIsOpenAtATime() {
        withShoppingList()
        show()
        waitForText("Eggs")
        rule.onNodeWithText(str(Res.string.step_add)).performClick()
        waitFor { fields() == 1 }

        rule.onNodeWithText("Eggs").performTouchInput { longClick() }
        rule.waitForIdle()

        assertEquals(1, fields())
        // It is the step's field now, and "+ Step" is a row again.
        rule.onNode(hasSetTextAction() and hasText("Eggs")).assertExists()
        rule.onNodeWithText(str(Res.string.step_add)).assertExists()
    }

    @Test
    fun aStepOffersRenameMoveAndDeleteToTalkBack() {
        withShoppingList(Subtask(id = 12, taskId = 1, title = "Bread", position = 2))
        show()
        waitForText("Bread")

        val edit = str(Res.string.edit)
        val up = str(Res.string.tag_move_up)
        val down = str(Res.string.tag_move_down)
        val delete = str(Res.string.subtask_delete)
        assertEquals(listOf(edit, down, delete), customActions(rule.onNodeWithText("Milk")))
        assertEquals(listOf(edit, up, down, delete), customActions(rule.onNodeWithText("Eggs")))
        assertEquals(listOf(edit, up, delete), customActions(rule.onNodeWithText("Bread")))
    }

    @Test
    fun movingTheOneStepOfAFoldedTaskOpensItsSteps() {
        app.withTasks(Task(id = 1, title = "Trip"))
        app.tasks.subtasks.value = List(2) { Subtask(id = 10L + it, taskId = 1, title = "Step $it", position = it) }
        show()
        waitForText("Step 0")

        rename("Step 0")
        barButton(Res.string.tag_move_up).assertIsNotEnabled()
        barButton(Res.string.tag_move_down).performClick()

        waitFor { steps() == listOf("Step 1", "Step 0") }
        // Both are in sight now, the moved one still in its field.
        waitForText("Step 1")
        assertEquals(1, fields())
    }

    @Test
    fun aDoubleTapOnBackLeavesTheScreenOnceAndNeverEmptiesTheWindow() {
        app.withTasks(Task(id = 1, title = "Call mum"))
        setNavScreen(viewModels(app.tasksVm()), route = TestRoutes.TASKS, start = NavRoutes.HomeRoute.name) { nav ->
            TasksScreen(nav)
        }
        navigateTo(NavRoutes.TasksRoute.name)
        waitForText("Call mum")
        rule.mainClock.autoAdvance = false

        // The second tap lands while the screen is still on its way out.
        val back = rule.onNodeWithContentDescription(str(Res.string.back))
        back.performClick()
        back.performClick()

        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(NavRoutes.HomeRoute.name, currentRoute())
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
