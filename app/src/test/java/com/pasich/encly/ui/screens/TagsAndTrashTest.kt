package com.pasich.encly.ui.screens

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.data.model.Tag
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.EditTagScreen
import com.pasich.encly.presentation.screen.trash.TrashScreen
import org.junit.Assert.assertEquals
import org.junit.Test

class TagsAndTrashTest : ComposeScreenTest() {
    private val app by lazy { TestApp(context) }

    private fun showTags() {
        setNavScreen(viewModels(app.tagList()), route = NavRoutes.EditTagRoute.name) { nav -> EditTagScreen(nav) }
    }

    private fun showTrash() {
        setNavScreen(viewModels(app.trash()), route = NavRoutes.TrashRoute.name) { nav -> TrashScreen(nav) }
    }

    private fun trashed(vararg titles: String) {
        app.notes.trashNotes.value = titles.mapIndexed { index, title ->
            Note(id = index + 1L, title = title, value = "[]", isTrash = true, date = 1_700_000_000_000)
        }
    }

    @Test
    fun noTagsShowsTheFieldAndTheEmptyState() {
        showTags()

        waitForText(str(R.string.tags_empty_title))
        rule.onNodeWithText(str(R.string.tags_empty_desc)).assertIsDisplayed()
        rule.onNode(hasSetTextAction()).assertExists()
        assertBodyNotEmpty(minTexts = 3)
    }

    @Test
    fun tagsAreListedWithTheirNoteCounts() {
        val work = Tag(id = 1, nameTag = "Work")
        app.withTags(work, Tag(id = 2, nameTag = "Home", isVisible = false, position = 1))
        app.notes.allNotesWithTags.value = listOf(
            NoteWithTag(Note(id = 1, title = "a", tagId = 1), work),
            NoteWithTag(Note(id = 2, title = "b", tagId = 1), work),
        )
        showTags()

        waitForText("Work")
        rule.onNodeWithText("Home").assertIsDisplayed()
        rule.onNodeWithText(str(R.string.tag_hidden)).assertIsDisplayed()
        waitForText("2")
        rule.onNodeWithText(str(R.string.tags_hint)).assertIsDisplayed()
    }

    @Test
    fun aNewTagIsCreatedFromTheField() {
        showTags()
        waitForText(str(R.string.tags_empty_title))

        rule.onNode(hasSetTextAction()).performTextInput("Travel")
        rule.onNodeWithText(str(R.string.tag_add)).assertIsEnabled().performClick()

        waitFor { app.tags.tags.value.any { it.nameTag == "Travel" } }
        waitForText("Travel")
    }

    @Test
    fun aTakenNameIsFlaggedAsYouType() {
        app.withTags(Tag(id = 1, nameTag = "Work"))
        showTags()
        waitForText("Work")

        rule.onNode(hasSetTextAction()).performTextInput("work")

        waitForText(str(R.string.tag_name_taken))
        rule.onNodeWithText(str(R.string.tag_add)).assertIsNotEnabled()
    }

    @Test
    fun tappingATagOpensItsSheet() {
        app.withTags(Tag(id = 1, nameTag = "Work"))
        showTags()
        waitForText("Work")

        rule.onNode(hasText("Work") and hasClickAction()).performClick()

        waitForText(str(R.string.tag_edit))
        rule.onNodeWithText(str(R.string.tag_show_in_list)).assertExists()
        rule.onNodeWithText(str(R.string.delete_tag)).assertExists()
    }

    @Test
    fun aTagMovesDownWithTheAccessibilityAction() {
        app.withTags(Tag(id = 1, nameTag = "Work"), Tag(id = 2, nameTag = "Home", position = 1))
        showTags()
        waitForText("Home")

        val actions = rule.onNode(hasText("Work") and hasClickAction()).fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        rule.runOnUiThread { actions.single { it.label == str(R.string.tag_move_down) }.action() }

        waitFor { app.tags.tags.value.map { it.nameTag } == listOf("Home", "Work") }
    }

    @Test
    fun anEmptyTrashSaysSo() {
        showTrash()

        waitForText(str(R.string.empty_trash_title))
        rule.onNodeWithText(str(R.string.empty_trash_desc)).assertIsDisplayed()
        assertBodyNotEmpty(minTexts = 2)
    }

    @Test
    fun trashedNotesAreListedWithTheHint() {
        trashed("Old plan", "Draft")
        showTrash()

        waitForText("Old plan")
        rule.onNodeWithText("Draft").assertIsDisplayed()
        rule.onNodeWithText(str(R.string.trash_hint)).assertIsDisplayed()
    }

    @Test
    fun longPressSelectsAndOffersRestoreAndDeleteForever() {
        trashed("Old plan", "Draft")
        showTrash()
        waitForText("Old plan")

        rule.onNode(hasText("Old plan") and hasClickAction()).performTouchInput { longClick() }

        waitForText(plural(R.plurals.trash_selected, 1, 1))
        rule.onNodeWithContentDescription(str(R.string.restore)).assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.delete_forever)).assertIsDisplayed()
    }

    @Test
    fun restoringTheSelectionTakesTheNoteOutOfTheTrash() {
        trashed("Old plan")
        showTrash()
        waitForText("Old plan")
        rule.onNode(hasText("Old plan") and hasClickAction()).performTouchInput { longClick() }
        waitForText(plural(R.plurals.trash_selected, 1, 1))

        rule.onNodeWithContentDescription(str(R.string.restore)).performClick()

        waitFor { app.notes.updatedNotes.any { it.id == 1L && !it.isTrash } }
    }

    @Test
    fun tappingATrashedNoteOpensItReadOnly() {
        trashed("Old plan")
        showTrash()
        waitForText("Old plan")

        rule.onNode(hasText("Old plan") and hasClickAction()).performClick()

        waitFor { currentRoute() == TestRoutes.EDIT_NOTE }
        assertEquals(true, rule.runOnIdle { nav.currentBackStackEntry?.arguments?.getBoolean("isReadTrashOnly") })
    }
}
