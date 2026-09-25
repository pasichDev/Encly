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
}
