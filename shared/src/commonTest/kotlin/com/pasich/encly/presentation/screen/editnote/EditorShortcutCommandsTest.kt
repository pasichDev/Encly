package com.pasich.encly.presentation.screen.editnote

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorShortcutCommandsTest {
    @Test
    fun deleteIsOfferedOnlyForANoteThisWindowEditsAndThatHasContent() {
        assertTrue(canMoveToTrashByShortcut(trashOnly = false, blankDraft = false, editedElsewhere = false))
        assertFalse(canMoveToTrashByShortcut(trashOnly = true, blankDraft = false, editedElsewhere = false))
        assertFalse(canMoveToTrashByShortcut(trashOnly = false, blankDraft = true, editedElsewhere = false))
        assertFalse(canMoveToTrashByShortcut(trashOnly = false, blankDraft = false, editedElsewhere = true))
    }
}
