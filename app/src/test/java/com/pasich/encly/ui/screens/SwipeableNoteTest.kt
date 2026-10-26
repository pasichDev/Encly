package com.pasich.encly.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.pasich.encly.R
import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.model.NoteColor
import com.pasich.encly.domain.model.SwipeAction
import com.pasich.encly.domain.model.SwipeSettings
import com.pasich.encly.presentation.components.swipe.SwipeableNote
import com.pasich.encly.presentation.viewmodel.NoteListEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeableNoteTest : ComposeScreenTest() {
    private val events = mutableListOf<NoteListEvent>()

    private fun show(note: Note = Note(id = 1, title = "Plan"), settings: SwipeSettings = SwipeSettings()) {
        setScreen {
            SwipeableNote(note = note, settings = settings, onEvent = { events += it }) { cardModifier ->
                Box(cardModifier.fillMaxWidth().height(CARD_HEIGHT).testTag(CARD))
            }
        }
    }

    private fun swipeRightOnCard() {
        rule.onNodeWithTag(CARD).performTouchInput { swipeRight() }
        rule.waitForIdle()
    }

    private fun swipeLeftOnCard() {
        rule.onNodeWithTag(CARD).performTouchInput { swipeLeft() }
        rule.waitForIdle()
    }

    private fun customActionLabels(): List<String> = rule.onNodeWithTag(CARD).fetchSemanticsNode().config
        .getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }

    @Test
    fun swipingRightPinsAndSwipingLeftArchivesByDefault() {
        val note = Note(id = 1, title = "Plan")
        show(note)

        swipeRightOnCard()
        swipeLeftOnCard()

        assertEquals(listOf(NoteListEvent.PinNote(note, true), NoteListEvent.ArchiveNote(note, true)), events)
    }

    @Test
    fun aPinnedNoteIsUnpinnedBySwipingRight() {
        val note = Note(id = 1, isPinned = true)
        show(note)

        swipeRightOnCard()

        assertEquals(listOf(NoteListEvent.PinNote(note, false)), events)
    }

    @Test
    fun theColourSwipeAsksForTheNextColour() {
        val note = Note(id = 1, color = "pink")
        show(note, SwipeSettings(right = SwipeAction.NEXT_COLOR))

        swipeRightOnCard()

        assertEquals(listOf(NoteListEvent.ChangeColor(note, NoteColor.NONE)), events)
    }

    @Test
    fun deletingAsksFirstAndNothingHappensUntilConfirmed() {
        val note = Note(id = 1)
        show(note, SwipeSettings(left = SwipeAction.DELETE))

        swipeLeftOnCard()

        waitForText(str(R.string.swipe_delete_title))
        assertTrue(events.isEmpty())
        rule.onNodeWithText(str(R.string.dialog_confirm)).performClick()
        waitFor { events.isNotEmpty() }
        assertEquals(listOf(NoteListEvent.NoteToTrash(note)), events)
    }

    @Test
    fun cancellingTheDeleteConfirmationChangesNothing() {
        show(settings = SwipeSettings(left = SwipeAction.DELETE))

        swipeLeftOnCard()
        waitForText(str(R.string.swipe_delete_title))
        rule.onNodeWithText(str(R.string.dialog_dismiss)).performClick()

        waitFor { countText(str(R.string.swipe_delete_title)) == 0 }
        assertTrue(events.isEmpty())
    }

    @Test
    fun withSwipesOffNothingHappensAndNoActionsAreOffered() {
        show(settings = SwipeSettings(enabled = false))

        swipeRightOnCard()
        swipeLeftOnCard()

        assertTrue(events.isEmpty())
        assertTrue(customActionLabels().isEmpty())
    }

    @Test
    fun aDirectionSetToNothingDoesNotSwipe() {
        show(settings = SwipeSettings(right = SwipeAction.NONE))

        swipeRightOnCard()

        assertTrue(events.isEmpty())
        assertEquals(listOf(str(R.string.note_archive)), customActionLabels())
    }

    @Test
    fun theConfiguredActionsAreOfferedToTalkBack() {
        show(Note(id = 1, isPinned = true))

        assertEquals(listOf(str(R.string.note_unpin), str(R.string.note_archive)), customActionLabels())
    }

    @Test
    fun aTalkBackActionRunsLikeTheSwipe() {
        val note = Note(id = 1)
        show(note)

        rule.onNodeWithTag(CARD).fetchSemanticsNode().config[SemanticsActions.CustomActions].first().action()
        rule.waitForIdle()

        assertEquals(listOf(NoteListEvent.PinNote(note, true)), events)
    }

    @Test
    fun inTheArchivePinDoesNotApplyAndArchiveUnarchives() {
        val archived = Note(id = 1, isArchived = true)
        show(archived)

        swipeRightOnCard()
        swipeLeftOnCard()

        assertEquals(listOf(NoteListEvent.ArchiveNote(archived, false)), events)
        assertEquals(listOf(str(R.string.note_unarchive)), customActionLabels())
    }

    private companion object {
        const val CARD = "card"
        val CARD_HEIGHT = 80.dp
    }
}
