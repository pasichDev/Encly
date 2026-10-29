package com.pasich.encly.presentation.pane

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Background tabs (open in a new tab) and stepping through the tabs from the keyboard. */
class NoteTabsBackgroundTest {
    private val tabs = NoteTabs()

    private fun editorCountingSaves(counter: IntArray) = ActiveEditor(
        save = {
            counter[0]++
            true
        },
        requestClose = {},
    )

    @Test
    fun aBackgroundTabIsAddedWithoutLeavingTheShownOne() = runTest {
        val saves = IntArray(1)
        tabs.attach(editorCountingSaves(saves))
        tabs.open(1)
        val shown = tabs.activeKey

        assertTrue(tabs.openInBackground(noteId = 2, title = "Trip", epoch = 5L))

        assertEquals(listOf(1L, 2L), tabs.tabs.map { it.noteId })
        assertEquals("Trip", tabs.tabs.last().title)
        assertEquals(5L, tabs.tabs.last().epoch)
        assertEquals(shown, tabs.activeKey)
        assertEquals(0, saves[0])
    }

    @Test
    fun aNoteThatHasATabAlreadyGetsNoSecondOne() = runTest {
        tabs.open(1)
        tabs.openInBackground(noteId = 2, title = "Trip")

        assertTrue(tabs.openInBackground(noteId = 2, title = "Trip"))

        assertEquals(2, tabs.tabs.size)
    }

    @Test
    fun aNoteThatIsNotStoredYetHasNoBackgroundTab() = runTest {
        tabs.open(1)

        assertTrue(tabs.openInBackground(noteId = -1, title = ""))

        assertEquals(1, tabs.tabs.size)
    }

    @Test
    fun withNothingShownABackgroundTabIsShown() = runTest {
        assertTrue(tabs.openInBackground(noteId = 3, title = "Trip"))

        assertEquals(3L, tabs.active?.noteId)
    }

    @Test
    fun cyclingShowsTheNextTabAndWrapsAround() = runTest {
        tabs.open(1)
        tabs.open(2)
        tabs.open(3)

        assertTrue(tabs.cycle(forward = true))
        assertEquals(1L, tabs.active?.noteId)
        assertTrue(tabs.cycle(forward = true))
        assertEquals(2L, tabs.active?.noteId)
    }

    @Test
    fun cyclingBackwardWrapsToTheLastTab() = runTest {
        tabs.open(1)
        tabs.open(2)
        tabs.activate(tabs.tabs.first().key)

        assertTrue(tabs.cycle(forward = false))

        assertEquals(2L, tabs.active?.noteId)
    }

    @Test
    fun cyclingSavesTheTabItLeaves() = runTest {
        val saves = IntArray(1)
        tabs.attach(editorCountingSaves(saves))
        tabs.open(1)
        tabs.open(2)
        val before = saves[0]

        tabs.cycle(forward = true)

        assertEquals(before + 1, saves[0])
    }

    @Test
    fun withFewerThanTwoTabsThereIsNothingToCycleTo() = runTest {
        assertFalse(tabs.cycle(forward = true))
        tabs.open(1)
        assertFalse(tabs.cycle(forward = false))
        assertEquals(1L, tabs.active?.noteId)
    }
}
