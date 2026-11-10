package com.pasich.encly.presentation.pane

import com.pasich.encly.core.security.VaultLockEvents
import com.pasich.encly.presentation.navigation.RelockReturn
import com.pasich.encly.presentation.viewmodel.NoteTabsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteTabsTest {
    private val tabs = NoteTabs()

    /** An editor for the active tab: [saves] counts its saves, [closes] its close requests. */
    private inner class FakeEditor(var result: Boolean = true) {
        var saves = 0
        var closes = 0
        val editor = ActiveEditor(
            save = {
                saves++
                result
            },
            requestClose = { closes++ },
        )

        fun attach() = tabs.attach(editor)
    }

    @Test
    fun aNoteIsOpenedInANewActiveTab() = runTest {
        assertTrue(tabs.open(noteId = 7))

        assertEquals(listOf(7L), tabs.tabs.map { it.noteId })
        assertEquals(tabs.tabs.single().key, tabs.activeKey)
    }

    @Test
    fun aNoteThatIsAlreadyOpenIsShownInItsTabInsteadOfASecondOne() = runTest {
        tabs.open(1)
        tabs.open(2)
        val first = tabs.tabs.first().key

        assertTrue(tabs.open(1))

        assertEquals(2, tabs.tabs.size)
        assertEquals(first, tabs.activeKey)
    }

    @Test
    fun twoNewNotesAreTwoTabs() = runTest {
        FakeEditor().attach()
        tabs.open(-1)
        // The first one has an id by now (the editor stored it), so it is not dropped as blank.
        tabs.report(tabs.activeKey!!, noteId = 9, title = "A")
        tabs.open(-1)

        assertEquals(listOf(9L, -1L), tabs.tabs.map { it.noteId })
    }

    @Test
    fun leavingATabSavesItFirst() = runTest {
        val editor = FakeEditor().also { it.attach() }
        tabs.open(1)
        tabs.open(2)

        assertEquals(1, editor.saves)
        assertEquals(2L, tabs.active?.noteId)
    }

    @Test
    fun aFailedSaveKeepsTheNoteOnScreen() = runTest {
        val editor = FakeEditor(result = false).also { it.attach() }
        tabs.open(1)
        val shown = tabs.activeKey

        assertFalse(tabs.open(2))

        assertEquals(1, editor.saves)
        assertEquals(shown, tabs.activeKey)
        assertEquals(1, tabs.tabs.size)
    }

    @Test
    fun aBlankNewNoteLeavesNoTabWhenItIsLeft() = runTest {
        FakeEditor().attach()
        tabs.open(-1)
        tabs.open(5)

        assertEquals(listOf(5L), tabs.tabs.map { it.noteId })
    }

    @Test
    fun anEditorThatStoredItsNoteKeepsItsTabWhenLeft() = runTest {
        tabs.attach(
            ActiveEditor(
                save = {
                    tabs.report(tabs.activeKey!!, noteId = 11, title = "Draft")
                    true
                },
                requestClose = {},
            ),
        )
        tabs.open(-1)
        tabs.open(5)

        assertEquals(listOf(11L, 5L), tabs.tabs.map { it.noteId })
        assertEquals("Draft", tabs.tabs.first().title)
    }

    @Test
    fun theShownTabIsClosedThroughItsEditorSoItSavesFirst() = runTest {
        val editor = FakeEditor().also { it.attach() }
        tabs.open(1)

        tabs.close(tabs.activeKey!!)

        assertEquals(1, editor.closes)
        // Still there until the editor, once saved, removes it.
        assertEquals(1, tabs.tabs.size)
    }

    @Test
    fun aBackgroundTabJustGoes() = runTest {
        val editor = FakeEditor().also { it.attach() }
        tabs.open(1)
        tabs.open(2)
        val background = tabs.tabs.first().key

        tabs.close(background)

        assertEquals(0, editor.closes)
        assertEquals(listOf(2L), tabs.tabs.map { it.noteId })
    }

    @Test
    fun removingTheShownTabShowsItsNeighbour() = runTest {
        tabs.open(1)
        tabs.open(2)
        tabs.open(3)
        tabs.activate(tabs.tabs[1].key)

        tabs.remove(tabs.activeKey!!)
        assertEquals(1L, tabs.active?.noteId)

        tabs.remove(tabs.activeKey!!)
        assertEquals(3L, tabs.active?.noteId)

        tabs.remove(tabs.activeKey!!)
        assertNull(tabs.activeKey)
        assertTrue(tabs.tabs.isEmpty())
    }

    @Test
    fun aCopyTakesThePlaceOfItsSource() = runTest {
        tabs.open(1)
        tabs.open(2)
        tabs.activate(tabs.tabs.first().key)

        tabs.replace(tabs.activeKey!!, noteId = 3)

        assertEquals(listOf(3L, 2L), tabs.tabs.map { it.noteId })
        assertEquals(3L, tabs.active?.noteId)
    }

    @Test
    fun anEditorThatLeftDoesNotDetachItsSuccessor() = runTest {
        val first = FakeEditor()
        val second = FakeEditor()
        tabs.open(1)
        first.attach()
        second.attach()

        tabs.detach(first.editor)
        tabs.open(2)

        assertEquals(1, second.saves)
        assertEquals(0, first.saves)
    }

    @Test
    fun clearingDropsEveryTabAndTitle() = runTest {
        tabs.open(1)
        tabs.report(tabs.activeKey!!, 1, "Secret title")
        tabs.open(2)

        tabs.clear()

        assertTrue(tabs.tabs.isEmpty())
        assertNull(tabs.activeKey)
        assertNull(tabs.active)
    }

    @Test
    fun anEditorStillLoadingDoesNotMakeItsStoredNoteLookBlank() = runTest {
        FakeEditor().attach()
        tabs.open(1)

        // The editor's first state, before the stored note is loaded.
        tabs.report(tabs.activeKey!!, noteId = -1, title = "")
        tabs.open(2)

        assertEquals(listOf(1L, 2L), tabs.tabs.map { it.noteId })
    }

    @Test
    fun reportingAClosedTabChangesNothing() = runTest {
        tabs.open(1)
        val key = tabs.activeKey!!
        tabs.remove(key)

        tabs.report(key, 1, "late")

        assertTrue(tabs.tabs.isEmpty())
    }

    @Test
    fun theTabsAreDroppedTheMomentTheSessionLocks() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val locked = MutableStateFlow(false)
            val viewModel = NoteTabsViewModel(
                object : VaultLockEvents {
                    override val locked: StateFlow<Boolean> = locked
                },
            )
            runTest(dispatcher) {
                viewModel.tabs.open(1)
                assertEquals(1, viewModel.tabs.tabs.size)

                locked.value = true

                assertTrue(viewModel.tabs.tabs.isEmpty())
                assertNull(viewModel.tabs.activeKey)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun theEditorRouteOfATabCarriesItsKeyAndArguments() {
        assertEquals("PaneEditor/4?tab=2", paneEditorRoute(NoteTab(key = 2, noteId = 4)))
        assertEquals(
            "PaneEditor/-1?copySource=4&tab=3",
            paneEditorRoute(NoteTab(key = 3, noteId = -1, params = "?copySource=4")),
        )
    }

    @Test
    fun aRelockInThePaneReturnsToTheShownNoteOnlyFromHome() {
        assertEquals("EditNoteRoute/42", RelockReturn.paneRouteFor("HomeRoute", openNoteId = 42))
        assertNull(RelockReturn.paneRouteFor("HomeRoute", openNoteId = -1))
        assertNull(RelockReturn.paneRouteFor("HomeRoute", openNoteId = null))
        assertNull(RelockReturn.paneRouteFor("SettingsRoute", openNoteId = 42))
        assertNull(RelockReturn.paneRouteFor(null, openNoteId = 42))
    }
}
