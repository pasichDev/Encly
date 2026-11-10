package com.pasich.encly.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.rightClick
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.duplicate
import com.pasich.encly.core.resources.note_archive
import com.pasich.encly.core.resources.note_color_title
import com.pasich.encly.core.resources.note_open
import com.pasich.encly.core.resources.note_open_in_new_tab
import com.pasich.encly.core.resources.note_pin
import com.pasich.encly.core.resources.note_unarchive
import com.pasich.encly.core.resources.note_unpin
import com.pasich.encly.core.resources.subtask_add
import com.pasich.encly.core.resources.subtask_first_step
import com.pasich.encly.core.resources.subtasks_show
import com.pasich.encly.core.resources.swipe_choice_delete
import com.pasich.encly.core.resources.task_edit_placeholder
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Task
import com.pasich.encly.domain.model.NoteColor
import com.pasich.encly.presentation.components.tiles.NoteContextActions
import com.pasich.encly.presentation.components.tiles.NoteItem
import com.pasich.encly.presentation.dialogs.NoteAction
import com.pasich.encly.presentation.screen.TasksScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a mouse adds to the lists, with the touch behaviour as it was: a right click on a note or a task opens
 * a menu of the actions the long-press sheet and TalkBack have, and a finger's tap and long press open none.
 */
@OptIn(ExperimentalTestApi::class)
class PointerMenusTest : ComposeScreenTest() {
    private val app by lazy { TestApp() }

    private val actions = mutableListOf<NoteAction>()
    private var opened = 0
    private var longPressed = 0
    private var newTab = 0

    private fun showNote(
        note: Note = Note(id = 1, title = "Plan"),
        menu: Boolean = true,
        tabs: Boolean = false,
        dimmed: Boolean = false,
    ) {
        setScreen {
            NoteItem(
                onItemClick = { opened++ },
                onItemLongClick = { longPressed++ },
                itemNote = note,
                dimmed = dimmed,
                contextActions = NoteContextActions(
                    onAction = { actions += it },
                    onOpenInNewTab = if (tabs) ({ newTab++ }) else null,
                ).takeIf { menu },
            )
        }
    }

    private fun rightClickNote() {
        rule.onNodeWithText("Plan").performMouseInput { rightClick() }
        rule.waitForIdle()
    }

    @Test
    fun aRightClickOnANoteOffersItsActionsAndRunsThePickedOne() {
        showNote()

        rightClickNote()
        rule.onNodeWithText(str(Res.string.note_open)).assertExists()
        rule.onNodeWithText(str(Res.string.note_archive)).assertExists()
        rule.onNodeWithText(str(Res.string.duplicate)).assertExists()
        rule.onNodeWithText(str(Res.string.swipe_choice_delete)).assertExists()
        rule.onNodeWithText(str(Res.string.note_open_in_new_tab)).assertDoesNotExist()
        rule.onNodeWithText(str(Res.string.note_pin)).performClick()
        rule.waitForIdle()

        assertEquals(listOf<NoteAction>(NoteAction.Pin(true)), actions)
        assertEquals("the card is not opened by the right click", 0, opened)
    }

    @Test
    fun everyEntryIsTheSheetsAction() {
        showNote(Note(id = 1, title = "Plan"), tabs = true)

        val picks = listOf(
            str(Res.string.note_open),
            str(Res.string.note_open_in_new_tab),
            str(Res.string.note_archive),
            str(Res.string.note_color_title) + ": " + str(NoteColor.RED.labelRes),
            str(Res.string.duplicate),
            str(Res.string.swipe_choice_delete),
        )
        picks.forEach { label ->
            rightClickNote()
            rule.onNodeWithText(label).performClick()
            rule.waitForIdle()
        }

        assertEquals(
            listOf(
                NoteAction.Edit,
                NoteAction.Archive(true),
                NoteAction.ChangeColor(NoteColor.RED),
                NoteAction.Duplicate,
                NoteAction.Delete,
            ),
            actions,
        )
        assertEquals(1, newTab)
    }

    @Test
    fun aPinnedNoteOffersUnpinAndAnArchivedOneUnarchiveAndNoPin() {
        showNote(Note(id = 1, title = "Plan", isPinned = true))
        rightClickNote()
        rule.onNodeWithText(str(Res.string.note_unpin)).assertExists()
    }

    @Test
    fun anArchivedNoteOffersUnarchiveAndNoPin() {
        showNote(Note(id = 1, title = "Plan", isArchived = true))

        rightClickNote()

        rule.onNodeWithText(str(Res.string.note_unarchive)).assertExists()
        rule.onNodeWithText(str(Res.string.note_pin)).assertDoesNotExist()
        rule.onNodeWithText(str(Res.string.note_unpin)).assertDoesNotExist()
    }

    @Test
    fun withoutActionsOrInTheTrashARightClickOpensNothing() {
        showNote(menu = false)
        rightClickNote()
        rule.onNodeWithText(str(Res.string.note_open)).assertDoesNotExist()
    }

    @Test
    fun theTrashHasNoMenu() {
        showNote(dimmed = true)

        rightClickNote()

        rule.onNodeWithText(str(Res.string.note_open)).assertDoesNotExist()
    }

    @Test
    fun aFingerStillOpensAndLongPressesTheNoteAndSeesNoMenu() {
        showNote()

        rule.onNodeWithText("Plan").performClick()
        rule.onNodeWithText("Plan").performTouchInput { longClick() }
        rule.waitForIdle()

        assertEquals(1, opened)
        assertEquals(1, longPressed)
        rule.onNodeWithText(str(Res.string.note_open)).assertDoesNotExist()
        assertTrue(actions.isEmpty())
    }

    // --- tasks ---------------------------------------------------------------------------------

    private fun showTasks() {
        app.withTasks(Task(id = 1, title = "Call mum"))
        setNavScreen(viewModels(app.tasksVm()), route = TestRoutes.TASKS) { nav ->
            TasksScreen(nav, openNewTask = false)
        }
        waitForText("Call mum")
    }

    @Test
    fun aRightClickOnATaskOffersWhatTalkBackHas() {
        showTasks()

        rule.onNodeWithText("Call mum").performMouseInput { rightClick() }
        rule.waitForIdle()

        rule.onNodeWithText(str(Res.string.task_edit_placeholder)).assertExists()
        rule.onNodeWithText(str(Res.string.subtask_add)).assertExists()
        // Picking the first one opens the task's tree, as a tap on the row does.
        rule.onNodeWithText(str(Res.string.subtasks_show)).performClick()
        waitForText(str(Res.string.subtask_first_step))
    }
}
