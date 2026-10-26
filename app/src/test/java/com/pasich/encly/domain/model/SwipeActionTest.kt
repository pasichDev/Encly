package com.pasich.encly.domain.model

import com.pasich.encly.data.model.Note
import com.pasich.encly.presentation.components.swipe.effectiveFor
import com.pasich.encly.presentation.components.swipe.toEvent
import com.pasich.encly.presentation.viewmodel.NoteListEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeActionTest {
    private val note = Note(id = 3, color = "none")

    @Test
    fun theDefaultsPinOnTheRightAndArchiveOnTheLeft() {
        val settings = SwipeSettings()

        assertEquals(SwipeAction.PIN, settings.actionFor(SwipeSide.RIGHT))
        assertEquals(SwipeAction.ARCHIVE, settings.actionFor(SwipeSide.LEFT))
    }

    @Test
    fun turnedOffThereIsNoActionInEitherDirection() {
        val settings = SwipeSettings(enabled = false)

        assertEquals(SwipeAction.NONE, settings.actionFor(SwipeSide.RIGHT))
        assertEquals(SwipeAction.NONE, settings.actionFor(SwipeSide.LEFT))
    }

    @Test
    fun eachDirectionHasItsOwnChoice() {
        val settings = SwipeSettings(right = SwipeAction.DELETE, left = SwipeAction.NEXT_COLOR)

        assertEquals(SwipeAction.DELETE, settings.actionFor(SwipeSide.RIGHT))
        assertEquals(SwipeAction.NEXT_COLOR, settings.actionFor(SwipeSide.LEFT))
    }

    @Test
    fun pinTogglesThePin() {
        assertEquals(NoteListEvent.PinNote(note, true), SwipeAction.PIN.toEvent(note))
        val pinned = note.copy(isPinned = true)
        assertEquals(NoteListEvent.PinNote(pinned, false), SwipeAction.PIN.toEvent(pinned))
    }

    @Test
    fun archiveTogglesTheArchiveAndUnarchivesInTheArchive() {
        assertEquals(NoteListEvent.ArchiveNote(note, true), SwipeAction.ARCHIVE.toEvent(note))
        val archived = note.copy(isArchived = true)
        assertEquals(NoteListEvent.ArchiveNote(archived, false), SwipeAction.ARCHIVE.toEvent(archived))
    }

    @Test
    fun pinMeansNothingForAnArchivedNote() {
        val archived = note.copy(isArchived = true)

        assertEquals(SwipeAction.NONE, SwipeAction.PIN.effectiveFor(archived))
        assertNull(SwipeAction.PIN.toEvent(archived))
    }

    @Test
    fun noneAndDeleteMapToWhatTheyShould() {
        assertNull(SwipeAction.NONE.toEvent(note))
        assertEquals(NoteListEvent.NoteToTrash(note), SwipeAction.DELETE.toEvent(note))
    }

    @Test
    fun theColourCyclesThroughThePaletteAndWrapsToNone() {
        var current = NoteColor.NONE
        val seen = mutableListOf<NoteColor>()
        repeat(NoteColor.entries.size) {
            val event = SwipeAction.NEXT_COLOR.toEvent(note.copy(color = current.key)) as NoteListEvent.ChangeColor
            seen += event.color
            current = event.color
        }

        assertEquals(
            listOf(
                NoteColor.RED, NoteColor.ORANGE, NoteColor.YELLOW, NoteColor.GREEN, NoteColor.TEAL,
                NoteColor.BLUE, NoteColor.PURPLE, NoteColor.PINK, NoteColor.NONE,
            ),
            seen,
        )
        assertEquals(NoteColor.RED, NoteColor.NONE.next())
        assertEquals(NoteColor.NONE, NoteColor.PINK.next())
    }

    @Test
    fun anUnknownStoredColourCountsAsNone() {
        val event = SwipeAction.NEXT_COLOR.toEvent(note.copy(color = "magenta")) as NoteListEvent.ChangeColor

        assertEquals(NoteColor.RED, event.color)
    }
}
