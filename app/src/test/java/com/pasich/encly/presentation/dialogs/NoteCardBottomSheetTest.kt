package com.pasich.encly.presentation.dialogs

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.pasich.encly.R
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.data.model.Tag
import com.pasich.encly.ui.screens.ComposeScreenTest
import com.pasich.encly.ui.screens.TestApp
import org.junit.Assert.assertEquals
import org.junit.Test

/** The long-press sheet of a note: what it shows, which action each row reports, and the description editor. */
@OptIn(ExperimentalMaterial3Api::class)
class NoteCardBottomSheetTest : ComposeScreenTest() {
    private val app by lazy { TestApp(context) }
    private val actions = mutableListOf<NoteAction>()
    private var dismissed = 0

    private fun show(
        note: Note = Note(id = 5, title = "Groceries", date = 1_700_000_000_000L),
        visible: Boolean = true,
    ) {
        app.tags.tags.value = listOf(Tag(id = 1, nameTag = "Work"), Tag(id = 2, nameTag = "Home"))
        setScreen(viewModels(app.tagList())) {
            NoteCardBottomSheet(
                isVisible = visible,
                item = NoteWithTag(note, null),
                onAction = { actions += it },
                onDismiss = { dismissed++ },
            )
        }
    }

    @Test
    fun aHiddenSheetShowsNothing() {
        show(visible = false)

        assertEquals(0, countText("Groceries"))
    }

    @Test
    fun theSheetShowsTheTitleTheTagChipsAndTheRows() {
        show()

        assertEquals(1, countText("Groceries"))
        waitForText("Work")
        listOf("Home", str(R.string.no_tag), str(R.string.edit), str(R.string.duplicate), str(R.string.delete))
            .forEach { assertEquals(it, 1, countText(it)) }
    }

    @Test
    fun aNoteWithoutATitleIsCalledUntitled() {
        show(Note(id = 5, title = "", date = 1L))

        assertEquals(1, countText(str(R.string.untitled)))
    }

    @Test
    fun editAndDuplicateReportTheirActions() {
        show()

        rule.onNodeWithText(str(R.string.edit)).performClick()
        rule.onNodeWithText(str(R.string.duplicate)).performClick()

        assertEquals(listOf(NoteAction.Edit, NoteAction.Duplicate), actions)
    }

    @Test
    fun deletingTakesASecondTap() {
        show()

        rule.onNodeWithText(str(R.string.delete)).performClick()
        rule.waitForIdle()
        assertEquals(emptyList<NoteAction>(), actions)

        rule.onNodeWithText(str(R.string.tap_again_to_delete)).performClick()
        assertEquals(listOf<NoteAction>(NoteAction.Delete), actions)
    }

    @Test
    fun aTagChipMovesTheNoteToThatTagAndNoTagClearsIt() {
        show(Note(id = 5, title = "Groceries", tagId = 2))
        waitForText("Work")

        rule.onNodeWithText("Work").performClick()
        rule.onNodeWithText(str(R.string.no_tag)).performClick()

        assertEquals(listOf(NoteAction.ChangeTag(1), NoteAction.ChangeTag(0)), actions)
    }

    @Test
    fun theDescriptionRowShowsItsPlaceholderWhenEmptyAndTheTextWhenNot() {
        show(Note(id = 5, title = "Groceries", description = "Weekly shop"))
        assertEquals(1, countText("Weekly shop"))
        assertEquals(0, countText(str(R.string.note_description_placeholder)))
    }

    @Test
    fun anEmptyDescriptionShowsThePlaceholder() {
        show()

        assertEquals(1, countText(str(R.string.note_description_placeholder)))
    }

    @Test
    fun savingADescriptionReportsItTrimmedAndLeavesEditing() {
        show()

        rule.onNodeWithText(str(R.string.note_description_placeholder)).performClick()
        rule.waitForIdle()
        assertEquals("the other rows make way for the field", 0, countText(str(R.string.duplicate)))
        rule.onNode(hasSetTextAction()).performTextInput("  Buy milk  ")
        rule.onNodeWithText(str(R.string.save)).performClick()
        rule.waitForIdle()

        assertEquals(listOf<NoteAction>(NoteAction.ChangeDescription("Buy milk")), actions)
        assertEquals("the rows are back", 1, countText(str(R.string.duplicate)))
    }

    @Test
    fun cancellingTheDescriptionEditReportsNothing() {
        show(Note(id = 5, title = "Groceries", description = "Weekly shop"))

        rule.onNodeWithText("Weekly shop").performClick()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).performTextInput(" and more")
        rule.onNodeWithText(str(R.string.cancel)).performClick()
        rule.waitForIdle()

        assertEquals(emptyList<NoteAction>(), actions)
        assertEquals(1, countText("Weekly shop"))
        assertEquals(1, countText(str(R.string.duplicate)))
    }
}
