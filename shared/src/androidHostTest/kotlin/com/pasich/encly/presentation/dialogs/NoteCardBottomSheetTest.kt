package com.pasich.encly.presentation.dialogs

import androidx.activity.ComponentDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.back
import com.pasich.encly.core.resources.delete
import com.pasich.encly.core.resources.duplicate
import com.pasich.encly.core.resources.edit
import com.pasich.encly.core.resources.main_drawer_tags
import com.pasich.encly.core.resources.no_tag
import com.pasich.encly.core.resources.note_archive
import com.pasich.encly.core.resources.note_color_none
import com.pasich.encly.core.resources.note_color_teal
import com.pasich.encly.core.resources.note_color_title
import com.pasich.encly.core.resources.note_description_none
import com.pasich.encly.core.resources.note_description_placeholder
import com.pasich.encly.core.resources.note_pin
import com.pasich.encly.core.resources.note_unarchive
import com.pasich.encly.core.resources.note_unpin
import com.pasich.encly.core.resources.save
import com.pasich.encly.core.resources.tap_again_to_delete
import com.pasich.encly.core.resources.task_description_placeholder
import com.pasich.encly.core.resources.untitled
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTags
import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.model.NoteColor
import com.pasich.encly.domain.model.NoteListItem
import com.pasich.encly.presentation.screen.NoteActionsSheet
import com.pasich.encly.presentation.screen.NoteSheetState
import com.pasich.encly.ui.screens.ComposeScreenTest
import com.pasich.encly.ui.screens.TestApp
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.shadows.ShadowDialog

/**
 * The long-press sheet of a note: its rows and their values, the pages the tags, colour and description
 * rows lead to (and the way back), and which action each row reports.
 */
@OptIn(ExperimentalMaterial3Api::class)
class NoteCardBottomSheetTest : ComposeScreenTest() {
    private val app by lazy { TestApp() }
    private val actions = mutableListOf<NoteAction>()
    private var dismissed = 0

    private val tags = listOf(Tag(id = 1, nameTag = "Work"), Tag(id = 2, nameTag = "Home"))

    private fun show(
        note: Note = Note(id = 5, title = "Groceries", date = 1_700_000_000_000L),
        visible: Boolean = true,
    ) {
        app.tags.tags.value = tags
        setScreen(viewModels(app.tagList())) {
            NoteCardBottomSheet(
                isVisible = visible,
                item = NoteWithTags(note, tags.filter { it.id in note.tagIds }),
                onAction = { actions += it },
                onDismiss = { dismissed++ },
            )
        }
    }

    private fun openPage(title: String) {
        rule.onNodeWithText(title).performClick()
        rule.waitForIdle()
        assertEquals("the rows made way for the page", 0, countText(str(Res.string.duplicate)))
    }

    private fun pageHeight() = rule.onNodeWithTag(NOTE_SHEET_PAGE_TAG).fetchSemanticsNode().size.height

    /** The system's Back, as the sheet's own window gets it. */
    private fun pressBack() {
        rule.runOnUiThread {
            (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        }
        rule.waitForIdle()
    }

    @Test
    fun aHiddenSheetShowsNothing() {
        show(visible = false)

        assertEquals(0, countText("Groceries"))
    }

    @Test
    fun aPlainNoteShowsItsRowsWithNothingSetYet() {
        show()

        assertEquals(1, countText("Groceries"))
        listOf(
            str(Res.string.main_drawer_tags),
            str(Res.string.no_tag),
            str(Res.string.note_color_title),
            str(Res.string.note_color_none),
            str(Res.string.task_description_placeholder),
            str(Res.string.note_description_none),
            str(Res.string.edit),
            str(Res.string.note_pin),
            str(Res.string.note_archive),
            str(Res.string.duplicate),
            str(Res.string.delete),
        ).forEach { assertEquals(it, 1, countText(it)) }
        assertEquals(0, countText(str(Res.string.note_description_placeholder)))
    }

    @Test
    fun theRowsShowTheNotesTagsColourAndTheFirstLineOfItsDescription() {
        show(
            Note(
                id = 5,
                title = "Groceries",
                tagIds = setOf(1, 2),
                color = NoteColor.TEAL.key,
                description = "Weekly shop\nand the market",
                isPinned = true,
            ),
        )

        assertEquals(1, countText("Work, Home"))
        assertEquals(1, countText(str(Res.string.note_color_teal)))
        assertEquals(1, countText("Weekly shop"))
        assertEquals(0, countText(str(Res.string.note_description_none)))
        assertEquals(0, countText(str(Res.string.note_description_placeholder)))
        assertEquals("a pinned note offers Unpin", 1, countText(str(Res.string.note_unpin)))
    }

    @Test
    fun anArchivedNoteOffersUnarchiveAndNoPin() {
        show(Note(id = 5, title = "Groceries", isArchived = true))

        assertEquals(1, countText(str(Res.string.note_unarchive)))
        assertEquals(0, countText(str(Res.string.note_pin)))
        assertEquals(0, countText(str(Res.string.note_archive)))
    }

    @Test
    fun aNoteWithoutATitleIsCalledUntitled() {
        show(Note(id = 5, title = "", date = 1L))

        assertEquals(1, countText(str(Res.string.untitled)))
    }

    @Test
    fun editAndDuplicateReportTheirActions() {
        show()

        rule.onNodeWithText(str(Res.string.edit)).performClick()
        rule.onNodeWithText(str(Res.string.duplicate)).performClick()

        assertEquals(listOf(NoteAction.Edit, NoteAction.Duplicate), actions)
    }

    @Test
    fun deletingTakesASecondTap() {
        show()

        rule.onNodeWithText(str(Res.string.delete)).performClick()
        rule.waitForIdle()
        assertEquals(emptyList<NoteAction>(), actions)

        rule.onNodeWithText(str(Res.string.tap_again_to_delete)).performClick()
        assertEquals(listOf<NoteAction>(NoteAction.Delete), actions)
    }

    @Test
    fun theTagsPageTogglesEachTagAtOnceAndNoTagClearsThemAll() {
        show(Note(id = 5, title = "Groceries", tagIds = setOf(2)))
        openPage(str(Res.string.main_drawer_tags))
        waitForText("Work")

        rule.onNodeWithText("Work").performClick()
        rule.onNodeWithText("Home").performClick()
        rule.onNodeWithText(str(Res.string.no_tag)).performClick()

        assertEquals(
            listOf(
                NoteAction.ChangeTag(1, tagged = true),
                NoteAction.ChangeTag(2, tagged = false),
                NoteAction.ChangeTag(0, tagged = false),
            ),
            actions,
        )
        assertEquals("the page stays open for the next tag", 0, countText(str(Res.string.duplicate)))
    }

    @Test
    fun theColourPageAppliesASwatchAtOnce() {
        show()
        openPage(str(Res.string.note_color_title))

        rule.onNodeWithContentDescription(str(Res.string.note_color_teal)).performClick()

        assertEquals(listOf<NoteAction>(NoteAction.ChangeColor(NoteColor.TEAL)), actions)
    }

    @Test
    fun thePagesBackButtonReturnsToTheRows() {
        show()
        openPage(str(Res.string.note_color_title))

        rule.onNodeWithContentDescription(str(Res.string.back)).performClick()
        rule.waitForIdle()

        assertEquals(1, countText(str(Res.string.duplicate)))
        assertEquals(0, dismissed)
    }

    @Test
    fun systemBackOnAPageReturnsToTheRowsAndKeepsTheSheetOpen() {
        show()
        openPage(str(Res.string.main_drawer_tags))

        pressBack()

        assertEquals("back on the rows", 1, countText(str(Res.string.duplicate)))
        assertEquals("the sheet was not closed", 0, dismissed)
        assertEquals(1, countText("Groceries"))
    }

    @Test
    fun everyPageIsAsHighAsTheRows() {
        show(Note(id = 5, title = "Groceries", tagIds = setOf(2)))
        val rows = pageHeight()

        listOf(Res.string.main_drawer_tags, Res.string.note_color_title, Res.string.task_description_placeholder)
            .forEach { page ->
                openPage(str(page))
                assertEquals(str(page), rows, pageHeight())
                rule.onNodeWithContentDescription(str(Res.string.back)).performClick()
                rule.waitForIdle()
                assertEquals("back from ${str(page)}", rows, pageHeight())
            }
    }

    @Test
    fun savingADescriptionReportsItTrimmedAndReturnsToTheRows() {
        show()
        openPage(str(Res.string.task_description_placeholder))

        rule.onNode(hasSetTextAction()).performTextInput("  Buy milk  ")
        rule.onNodeWithText(str(Res.string.save)).performClick()
        rule.waitForIdle()

        assertEquals(listOf<NoteAction>(NoteAction.ChangeDescription("Buy milk")), actions)
        assertEquals("the rows are back", 1, countText(str(Res.string.duplicate)))
    }

    @Test
    fun goingBackFromTheDescriptionReportsNothing() {
        show(Note(id = 5, title = "Groceries", description = "Weekly shop"))
        openPage(str(Res.string.task_description_placeholder))

        rule.onNode(hasSetTextAction()).performTextInput(" and more")
        rule.onNodeWithContentDescription(str(Res.string.back)).performClick()
        rule.waitForIdle()

        assertEquals(emptyList<NoteAction>(), actions)
        assertEquals(1, countText("Weekly shop"))
        assertEquals(1, countText(str(Res.string.duplicate)))
    }

    @Test
    fun anActionRowRunsOnlyOnceTheSheetHasSlidAway() {
        val edited = mutableListOf<Long>()
        val note = Note(id = 5, title = "Groceries", date = 1L)
        app.tags.tags.value = tags
        setScreen(viewModels(app.tagList())) {
            val sheet =
                remember { NoteSheetState().apply { open(NoteListItem(NoteWithTags(note, emptyList()), null, "")) } }
            NoteActionsSheet(
                sheet = sheet,
                observeNote = { flowOf(NoteWithTags(note, emptyList())) },
                onEvent = {},
                onEdit = { edited += it.id },
                onDuplicate = {},
            )
        }
        rule.mainClock.autoAdvance = false

        rule.onNodeWithText(str(Res.string.edit)).performClick()
        rule.waitForIdle()
        repeat(2) { rule.mainClock.advanceTimeByFrame() }

        assertEquals("nothing runs under the sheet", emptyList<Long>(), edited)
        assertEquals("the sheet is still there, sliding away", 1, countText("Groceries"))

        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertEquals(listOf(5L), edited)
        assertEquals(0, countText("Groceries"))
    }
}
