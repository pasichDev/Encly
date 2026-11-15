package com.pasich.encly.ui.screens

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.delete_forever
import com.pasich.encly.core.resources.delete_tag
import com.pasich.encly.core.resources.empty_trash_desc
import com.pasich.encly.core.resources.empty_trash_title
import com.pasich.encly.core.resources.restore
import com.pasich.encly.core.resources.tag_add
import com.pasich.encly.core.resources.tag_edit
import com.pasich.encly.core.resources.tag_hidden
import com.pasich.encly.core.resources.tag_move_down
import com.pasich.encly.core.resources.tag_name_taken
import com.pasich.encly.core.resources.tag_show_in_list
import com.pasich.encly.core.resources.tags_empty_desc
import com.pasich.encly.core.resources.tags_empty_title
import com.pasich.encly.core.resources.tags_hint
import com.pasich.encly.core.resources.trash_days_left
import com.pasich.encly.core.resources.trash_empty
import com.pasich.encly.core.resources.trash_empty_confirm
import com.pasich.encly.core.resources.trash_first_in_days
import com.pasich.encly.core.resources.trash_hidden_note
import com.pasich.encly.core.resources.trash_keep
import com.pasich.encly.core.resources.trash_last_in_days
import com.pasich.encly.core.resources.trash_notes_count
import com.pasich.encly.core.resources.trash_section_later
import com.pasich.encly.core.resources.trash_section_soon
import com.pasich.encly.core.resources.trash_selected
import com.pasich.encly.core.resources.trash_summary
import com.pasich.encly.core.resources.trash_summary_same
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTags
import com.pasich.encly.data.model.Tag
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.EditTagScreen
import com.pasich.encly.presentation.screen.trash.TrashScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagsAndTrashTest : ComposeScreenTest() {
    private val app by lazy { TestApp() }

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

        waitForText(str(Res.string.tags_empty_title))
        rule.onNodeWithText(str(Res.string.tags_empty_desc)).assertIsDisplayed()
        rule.onNode(hasSetTextAction()).assertExists()
        assertBodyNotEmpty(minTexts = 3)
    }

    @Test
    fun tagsAreListedWithTheirNoteCounts() {
        val work = Tag(id = 1, nameTag = "Work")
        app.withTags(work, Tag(id = 2, nameTag = "Home", isVisible = false, position = 1))
        app.notes.allNotesWithTags.value = listOf(
            NoteWithTags(Note(id = 1, title = "a", tagIds = setOf(1)), listOf(work)),
            NoteWithTags(Note(id = 2, title = "b", tagIds = setOf(1)), listOf(work)),
        )
        showTags()

        waitForText("Work")
        rule.onNodeWithText("Home").assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.tag_hidden)).assertIsDisplayed()
        waitForText("2")
        rule.onNodeWithText(str(Res.string.tags_hint)).assertIsDisplayed()
    }

    @Test
    fun aNewTagIsCreatedFromTheField() {
        showTags()
        waitForText(str(Res.string.tags_empty_title))

        rule.onNode(hasSetTextAction()).performTextInput("Travel")
        rule.onNodeWithText(str(Res.string.tag_add)).assertIsEnabled().performClick()

        waitFor { app.tags.tags.value.any { it.nameTag == "Travel" } }
        waitForText("Travel")
    }

    @Test
    fun aTakenNameIsFlaggedAsYouType() {
        app.withTags(Tag(id = 1, nameTag = "Work"))
        showTags()
        waitForText("Work")

        rule.onNode(hasSetTextAction()).performTextInput("work")

        waitForText(str(Res.string.tag_name_taken))
        rule.onNodeWithText(str(Res.string.tag_add)).assertIsNotEnabled()
    }

    @Test
    fun tappingATagOpensItsSheet() {
        app.withTags(Tag(id = 1, nameTag = "Work"))
        showTags()
        waitForText("Work")

        rule.onNode(hasText("Work") and hasClickAction()).performClick()

        waitForText(str(Res.string.tag_edit))
        rule.onNodeWithText(str(Res.string.tag_show_in_list)).assertExists()
        rule.onNodeWithText(str(Res.string.delete_tag)).assertExists()
    }

    @Test
    fun aTagMovesDownWithTheAccessibilityAction() {
        app.withTags(Tag(id = 1, nameTag = "Work"), Tag(id = 2, nameTag = "Home", position = 1))
        showTags()
        waitForText("Home")

        val actions = rule.onNode(hasText("Work") and hasClickAction()).fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        rule.runOnUiThread { actions.single { it.label == str(Res.string.tag_move_down) }.action() }

        waitFor { app.tags.tags.value.map { it.nameTag } == listOf("Home", "Work") }
    }

    @Test
    fun anEmptyTrashSaysSo() {
        showTrash()

        waitForText(str(Res.string.empty_trash_title))
        rule.onNodeWithText(str(Res.string.empty_trash_desc)).assertIsDisplayed()
        assertBodyNotEmpty(minTexts = 2)
    }

    /** Notes trashed so many days ago, in the order given. */
    private fun trashedDaysAgo(vararg notes: Pair<String, Int?>) {
        val now = System.currentTimeMillis()
        app.notes.trashNotes.value = notes.mapIndexed { index, (title, daysAgo) ->
            // An hour over the day, so the count does not tip while the test runs.
            Note(
                id = index + 1L,
                title = title,
                value = "[]",
                isTrash = true,
                trashedAt = daysAgo?.let {
                    now - it * DAY -
                        HOUR
                },
            )
        }
    }

    @Test
    fun trashedNotesAreListedUnderALineThatSaysWhenTheyGo() {
        trashedDaysAgo("Shopping" to 0, "Trip plan" to 25, "Books" to 14)
        showTrash()

        waitForText("Trip plan")
        val notes = plural(Res.plurals.trash_notes_count, 3, 3)
        val first = plural(Res.plurals.trash_first_in_days, 3, 3)
        val last = plural(Res.plurals.trash_last_in_days, 28, 28)
        rule.onNodeWithText(str(Res.string.trash_summary, notes, first, last)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.trash_section_soon, 1).uppercase()).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.trash_section_later, 2).uppercase()).assertIsDisplayed()
        // Each note says the days it has left.
        rule.onNodeWithContentDescription(plural(Res.plurals.trash_days_left, 3, 3)).assertIsDisplayed()
        rule.onNodeWithContentDescription(plural(Res.plurals.trash_days_left, 14, 14)).assertIsDisplayed()
        rule.onNodeWithContentDescription(plural(Res.plurals.trash_days_left, 28, 28)).assertIsDisplayed()
    }

    @Test
    fun theNoteThatGoesFirstIsListedFirst() {
        trashedDaysAgo("Shopping" to 0, "Trip plan" to 25, "Books" to 14)
        showTrash()
        waitForText("Trip plan")

        val tops = listOf("Trip plan", "Books", "Shopping").map { title ->
            rule.onNode(hasText(title) and hasClickAction()).getBoundsInRoot().top
        }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun notesThatAllGoTheSameDaySayItOnce() {
        trashedDaysAgo("Shopping" to 2, "Books" to 2)
        showTrash()

        waitForText("Shopping")
        val notes = plural(Res.plurals.trash_notes_count, 2, 2)
        val left = plural(Res.plurals.trash_days_left, 26, 26)
        rule.onNodeWithText(str(Res.string.trash_summary_same, notes, left)).assertIsDisplayed()
    }

    @Test
    fun aTrashedNoteWithAHiddenTagShowsAPlaceholderInsteadOfItsTitle() {
        trashed("Secret plan", "Shopping")
        app.notes.trashTags.value = mapOf(1L to listOf(Tag(id = 1, nameTag = "Private", isVisible = false)))
        showTrash()

        waitForText("Shopping")
        rule.onNodeWithText(str(Res.string.trash_hidden_note)).assertIsDisplayed()
        rule.onNodeWithText("Secret plan").assertDoesNotExist()
    }

    @Test
    fun aNoteWithoutATrashTimeIsListedLaterWithTheWholeRetention() {
        trashedDaysAgo("Imported" to null)
        showTrash()

        waitForText("Imported")
        rule.onNodeWithText(str(Res.string.trash_section_later, 1).uppercase()).assertIsDisplayed()
        rule.onNodeWithContentDescription(plural(Res.plurals.trash_days_left, 28, 28)).assertIsDisplayed()
        // No date to show: the row is the days and the title.
        assertTrue(rule.onAllNodesWithText("Imported").fetchSemanticsNodes().size == 1)
    }

    @Test
    fun emptyOpensAConfirmationThatCountsTheNotesAndKeepLeavesThem() {
        trashed("Old plan", "Draft")
        showTrash()
        waitForText("Old plan")

        rule.onNodeWithText(str(Res.string.trash_empty)).performClick()

        waitForText(plural(Res.plurals.trash_empty_confirm, 2, 2, 28))
        rule.onNodeWithText(str(Res.string.trash_keep)).performClick()
        rule.waitForIdle()
        assertTrue(app.notes.deletedIds.isEmpty())
        rule.onNodeWithText("Old plan").assertIsDisplayed()
    }

    @Test
    fun confirmingEmptiesTheTrash() {
        trashed("Old plan", "Draft")
        showTrash()
        waitForText("Old plan")
        rule.onNodeWithText(str(Res.string.trash_empty)).performClick()
        waitForText(str(Res.string.trash_keep))

        // The red button of the pair, not the text action in the bar under the dialog.
        rule.onAllNodes(hasText(str(Res.string.trash_empty)) and hasClickAction()).onLast().performClick()

        waitFor { app.notes.deletedIds.toSet() == setOf(1L, 2L) }
    }

    @Test
    fun deletingTheSelectionForGoodAsksFirst() {
        trashed("Old plan", "Draft")
        showTrash()
        waitForText("Old plan")
        rule.onNode(hasText("Old plan") and hasClickAction()).performTouchInput { longClick() }
        waitForText(plural(Res.plurals.trash_selected, 1, 1))

        rule.onNodeWithContentDescription(str(Res.string.delete_forever)).performClick()
        waitForText(str(Res.string.trash_keep))
        assertTrue(app.notes.deletedIds.isEmpty())
        rule.onNode(hasText(str(Res.string.delete_forever)) and hasClickAction()).performClick()

        waitFor { app.notes.deletedIds == listOf(1L) }
    }

    @Test
    fun longPressSelectsAndOffersRestoreAndDeleteForever() {
        trashed("Old plan", "Draft")
        showTrash()
        waitForText("Old plan")

        rule.onNode(hasText("Old plan") and hasClickAction()).performTouchInput { longClick() }

        waitForText(plural(Res.plurals.trash_selected, 1, 1))
        rule.onNodeWithContentDescription(str(Res.string.restore)).assertIsDisplayed()
        rule.onNodeWithContentDescription(str(Res.string.delete_forever)).assertIsDisplayed()
    }

    @Test
    fun restoringTheSelectionTakesTheNoteOutOfTheTrash() {
        trashed("Old plan")
        showTrash()
        waitForText("Old plan")
        rule.onNode(hasText("Old plan") and hasClickAction()).performTouchInput { longClick() }
        waitForText(plural(Res.plurals.trash_selected, 1, 1))

        rule.onNodeWithContentDescription(str(Res.string.restore)).performClick()

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

    private companion object {
        const val HOUR = 60L * 60 * 1000
        const val DAY = 24 * HOUR
    }
}
