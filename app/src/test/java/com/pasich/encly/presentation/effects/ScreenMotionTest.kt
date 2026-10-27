package com.pasich.encly.presentation.effects

import com.pasich.encly.presentation.navigation.NavRoutes
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which motion swaps two screens: the note card grows into the editor, a re-lock never cross-fades. */
class ScreenMotionTest {
    private val home = NavRoutes.HomeRoute.name
    private val archive = NavRoutes.ArchiveRoute.name
    private val lock = NavRoutes.LockRoute.name
    private val settings = NavRoutes.SettingsRoute.name
    private val trash = NavRoutes.TrashRoute.name

    // The pattern a destination reports: the arguments are part of the route.
    private val editor = "${NavRoutes.EditNoteRoute.name}/{idNote}?isReadTrashOnly={isReadTrashOnly}"

    @Test
    fun theNoteListsAndTheEditorFadeInBothDirections() {
        assertEquals(ScreenMotion.FADE, screenMotion(home, editor))
        assertEquals(ScreenMotion.FADE, screenMotion(editor, home))
        assertEquals(ScreenMotion.FADE, screenMotion(archive, editor))
        assertEquals(ScreenMotion.FADE, screenMotion(editor, archive))
    }

    @Test
    fun anythingWithTheLockScreenHasNoMotion() {
        assertEquals(ScreenMotion.NONE, screenMotion(editor, lock))
        assertEquals(ScreenMotion.NONE, screenMotion(lock, home))
        assertEquals(ScreenMotion.NONE, screenMotion(home, lock))
        assertEquals(ScreenMotion.NONE, screenMotion(lock, editor))
        assertEquals(ScreenMotion.NONE, screenMotion(lock, lock))
    }

    @Test
    fun otherPairsKeepTheAxisMotion() {
        assertEquals(ScreenMotion.AXIS, screenMotion(home, settings))
        assertEquals(ScreenMotion.AXIS, screenMotion(settings, home))
        // The trash opens its notes read-only, without a card to grow from.
        assertEquals(ScreenMotion.AXIS, screenMotion(trash, editor))
        assertEquals(ScreenMotion.AXIS, screenMotion(editor, trash))
        // Duplicating a note opens a second editor.
        assertEquals(ScreenMotion.AXIS, screenMotion(editor, editor))
    }

    @Test
    fun unknownRoutesKeepTheAxisMotion() {
        assertEquals(ScreenMotion.AXIS, screenMotion(null, null))
        assertEquals(ScreenMotion.AXIS, screenMotion(null, home))
    }
}
