package com.pasich.encly.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.pasich.encly.R
import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.model.Task
import com.pasich.encly.domain.repository.NotesRepository
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.navigation.TASKS_ADD_ARG
import com.pasich.encly.presentation.screen.MainRootScreen
import com.pasich.encly.testutil.TestNotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class MainScreenTest : ComposeScreenTest() {
    private companion object {
        const val FADE_MS = 1_000L
    }

    private val app by lazy { TestApp(context) }

    private fun show(notesRepository: NotesRepository = app.notes) {
        setNavScreen(viewModels(*app.home(app.noteList(notesRepository))), route = NavRoutes.HomeRoute.name) { nav ->
            MainRootScreen(nav)
        }
    }

    private fun note(id: Long, title: String, text: String = "") = Note(
        id = id,
        title = title,
        value = BlockConverter.blocksToJson(listOf(Block.TextBlock(kotlinx.coroutines.flow.MutableStateFlow(text)))),
        date = 1_700_000_000_000 + id,
    )

    @Test
    fun noNotesShowsTheEmptyStateUnderTheHeader() {
        show()

        waitForText(str(R.string.empty_notes))
        rule.mainClock.advanceTimeBy(FADE_MS)
        rule.onNodeWithText(str(R.string.empty_notes_desc)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.search_placeholder)).assertExists()
        rule.onNodeWithText(str(R.string.note_add), useUnmergedTree = true).assertIsDisplayed()
        assertBodyNotEmpty(minTexts = 4)
    }

    @Test
    fun oneNoteIsListed() {
        app.withNotes(note(1, "Groceries", "milk, bread"))
        show()

        waitForText("Groceries")
        assertEquals(0, countText(str(R.string.empty_notes)))
    }

    @Test
    fun manyNotesAreListed() {
        app.withNotes(*Array(12) { note(it + 1L, "Note ${it + 1}", "body ${it + 1}") })
        show()

        waitForText("Note 12")
        assertBodyNotEmpty(minTexts = 8)
    }

    @Test
    fun aFailedReadIsAnErrorNeverAnEmptyVault() {
        val failing = object : NotesRepository by TestNotesRepository() {
            override fun getAllNotesWithTag(): Flow<List<NoteWithTag>> = flow { throw IOException("vault closed") }
        }
        show(failing)

        waitFor { visibleTexts().size > 3 }
        assertEquals(0, countText(str(R.string.empty_notes)))
        assertBodyNotEmpty(minTexts = 4)
    }

    @Test
    fun whileLoadingNeitherEmptyNorErrorIsShown() {
        val slow = object : NotesRepository by TestNotesRepository() {
            override fun getAllNotesWithTag(): Flow<List<NoteWithTag>> = MutableSharedFlow()
        }
        show(slow)

        rule.onNodeWithText(str(R.string.search_placeholder)).assertExists()
        assertEquals(0, countText(str(R.string.empty_notes)))
    }

    @Test
    fun longPressOpensTheNoteActions() {
        app.withNotes(note(1, "Groceries"))
        show()
        waitForText("Groceries")

        rule.onNode(hasText("Groceries") and hasClickAction()).performTouchInput { longClick() }

        waitForText(str(R.string.duplicate))
        rule.onNodeWithText(str(R.string.edit)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.delete)).assertIsDisplayed()
    }

    @Test
    fun deletingFromTheSheetMovesTheNoteToTheTrash() {
        app.withNotes(note(1, "Groceries"))
        show()
        waitForText("Groceries")
        rule.onNode(hasText("Groceries") and hasClickAction()).performTouchInput { longClick() }
        waitForText(str(R.string.delete))

        rule.onNodeWithText(str(R.string.delete)).performClick()
        rule.onNodeWithText(str(R.string.tap_again_to_delete)).assertIsDisplayed()
        assertEquals(emptyList<Note>(), app.notes.updatedNotes)
        rule.onNodeWithText(str(R.string.tap_again_to_delete)).performClick()

        waitFor(describe = { "${visibleTexts().map(::textOf)} ${app.notes.updatedNotes}" }) {
            app.notes.updatedNotes.any { it.id == 1L && it.isTrash }
        }
    }

    @Test
    fun editFromTheSheetOpensTheEditor() {
        app.withNotes(note(7, "Groceries"))
        show()
        waitForText("Groceries")
        rule.onNode(hasText("Groceries") and hasClickAction()).performTouchInput { longClick() }
        waitForText(str(R.string.edit))

        rule.onNodeWithText(str(R.string.edit)).performClick()

        waitFor { currentRoute() == TestRoutes.EDIT_NOTE }
        assertEquals(7L, rule.runOnIdle { nav.currentBackStackEntry?.arguments?.getLong("idNote") })
    }

    @Test
    fun tappingANoteOpensIt() {
        app.withNotes(note(3, "Groceries"))
        show()
        waitForText("Groceries")

        rule.onNode(hasText("Groceries") and hasClickAction()).performClick()

        waitFor { currentRoute() == TestRoutes.EDIT_NOTE }
        assertEquals(3L, rule.runOnIdle { nav.currentBackStackEntry?.arguments?.getLong("idNote") })
    }

    @Test
    fun theFabStartsANewNote() {
        show()
        waitForText(str(R.string.empty_notes))

        rule.onNodeWithText(str(R.string.note_add), useUnmergedTree = true).performClick()

        waitFor { currentRoute() == TestRoutes.EDIT_NOTE }
        assertEquals(-1L, rule.runOnIdle { nav.currentBackStackEntry?.arguments?.getLong("idNote") })
    }

    @Test
    fun searchShowsMatchesAndSaysWhenNothingMatches() {
        app.withNotes(note(1, "Groceries", "milk"), note(2, "Trip", "tickets"))
        show()
        waitForText("Trip")

        rule.onNode(hasSetTextAction()).performTextInput("zzz")

        waitForText(str(R.string.search_nothing_matches, "zzz"))
        rule.onNodeWithContentDescription(str(R.string.search_clear)).performClick()
        rule.onNode(hasSetTextAction()).performTextInput("tick")
        waitFor(describe = {
            "${visibleTexts().map(::textOf)}"
        }) { countText("Groceries") == 0 && countText("Trip") > 0 }
    }

    @Test
    fun withNoOpenTasksTheCardOffersANewOne() {
        show()

        waitForText(str(R.string.home_tasks_none_open), ignoreCase = true)
        rule.onNodeWithText(str(R.string.task_add)).performClick()

        waitFor { currentRoute() == TestRoutes.TASKS }
        assertEquals(true, rule.runOnIdle { nav.currentBackStackEntry?.arguments?.getBoolean(TASKS_ADD_ARG) })
    }

    @Test
    fun openTasksArePreviewedOnTheCard() {
        app.withTasks(Task(id = 1, title = "Pay rent", priority = 2))
        show()

        waitForText("Pay rent")
        rule.onNodeWithText(str(R.string.task_filter_all)).assertIsDisplayed()
    }

    @Test
    fun theTagChipsFilterAndAnEmptyTagOffersShowAll() {
        val work = Tag(id = 5, nameTag = "Work")
        app.withTags(work)
        app.withNotes(note(1, "Groceries"))
        show()
        waitForText("Groceries")

        rule.onNode(hasText("Work") and hasClickAction()).performClick()

        waitForText(str(R.string.empty_notes_tagged, "Work"))
        rule.onNodeWithText(str(R.string.show_all_notes)).performClick()
        waitForText("Groceries")
    }

    @Test
    fun theDrawerLeadsToEverySection() {
        show()
        waitForText(str(R.string.empty_notes))

        rule.onNodeWithContentDescription(str(R.string.open_menu)).performClick()

        waitForText(str(R.string.main_drawer_trash))
        listOf(R.string.main_drawer_tasks, R.string.main_drawer_tags, R.string.main_drawer_faq, R.string.about)
            .forEach { rule.onNodeWithText(str(it)).assertExists() }
        rule.onNodeWithText(str(R.string.main_drawer_trash)).performClick()
        waitFor { currentRoute() == NavRoutes.TrashRoute.name }
    }

    @Test
    fun theSettingsButtonOpensSettings() {
        show()

        rule.onNodeWithContentDescription(str(R.string.main_drawer_settings)).performClick()

        waitFor { currentRoute() == NavRoutes.SettingsRoute.name }
    }
}
