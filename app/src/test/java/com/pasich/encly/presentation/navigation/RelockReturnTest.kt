package com.pasich.encly.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelockReturnTest {
    private val editorRoute =
        "EditNoteRoute/{idNote}?isReadTrashOnly={isReadTrashOnly}&copySource={copySource}&addTag={addTag}"

    @Test
    fun aReLockInTheEditorReturnsToThatNote() {
        assertEquals("EditNoteRoute/42", RelockReturn.routeFor(editorRoute, openNoteId = 42L, readOnly = false))
    }

    @Test
    fun aNoteOpenedFromTheTrashReopensReadOnly() {
        assertEquals(
            "EditNoteRoute/7?isReadTrashOnly=true",
            RelockReturn.routeFor(editorRoute, openNoteId = 7L, readOnly = true),
        )
    }

    @Test
    fun aNeverSavedNoteOrAnotherScreenReturnsHome() {
        assertNull(RelockReturn.routeFor(editorRoute, openNoteId = -1L, readOnly = false))
        assertNull(RelockReturn.routeFor(editorRoute, openNoteId = null, readOnly = false))
        assertNull(RelockReturn.routeFor("HomeRoute", openNoteId = 42L, readOnly = false))
        assertNull(RelockReturn.routeFor(null, openNoteId = 42L, readOnly = false))
    }

    @Test
    fun aStoppedScreenIsKeptWhenNoUnlockHappenedElsewhere() {
        assertEquals(SessionResume.KEEP, SessionResume.afterStop(stoppedAt = null, generation = 3, locked = true))
        // A re-lock alone keeps the screens: the lock screen remembers the open note itself.
        assertEquals(SessionResume.KEEP, SessionResume.afterStop(stoppedAt = 3, generation = 3, locked = true))
        assertEquals(SessionResume.KEEP, SessionResume.afterStop(stoppedAt = 3, generation = 3, locked = false))
    }

    @Test
    fun anUnlockElsewhereWhileStoppedDropsTheOldScreens() {
        assertEquals(SessionResume.LOCK_SCREEN, SessionResume.afterStop(stoppedAt = 3, generation = 4, locked = true))
        assertEquals(SessionResume.HOME, SessionResume.afterStop(stoppedAt = 3, generation = 5, locked = false))
    }
}
