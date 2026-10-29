package com.pasich.encly.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoteHostServicesTest {
    private val registry = NoteEditRegistry()
    private val first = registry.forWindow("first")
    private val second = registry.forWindow("second")

    @Test
    fun aNoteHasOneEditorWindowAtATime() {
        assertTrue(first.claim(7))
        assertFalse(second.claim(7))
        assertTrue(registry.isClaimed(7))
    }

    @Test
    fun otherNotesAreNotAffected() {
        assertTrue(first.claim(7))
        assertTrue(second.claim(8))
    }

    @Test
    fun aWindowMayClaimItsNoteAgainWhileAnEditorIsBeingReplaced() {
        assertTrue(first.claim(7))
        // The new editor of the same note arrives before the old one is gone.
        assertTrue(first.claim(7))
        first.release(7)
        assertTrue(registry.isClaimed(7), "the replaced editor's claim is still held")
        assertFalse(second.claim(7))
        first.release(7)
        assertFalse(registry.isClaimed(7))
        assertTrue(second.claim(7))
    }

    @Test
    fun releasingGivesTheNoteBack() {
        assertTrue(first.claim(7))
        first.release(7)
        assertTrue(second.claim(7))
    }

    @Test
    fun aWindowCannotReleaseAnotherWindowsClaim() {
        assertTrue(first.claim(7))
        second.release(7)
        assertFalse(second.claim(7))
        assertTrue(registry.isClaimed(7))
    }

    @Test
    fun releasingWhatWasNeverClaimedChangesNothing() {
        first.release(7)
        assertFalse(registry.isClaimed(7))
    }
}
