package com.pasich.encly.presentation.navigation

import com.pasich.encly.presentation.designsystem.AppCommand
import com.pasich.encly.presentation.pane.ActiveEditor
import com.pasich.encly.presentation.pane.NoteTabs
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The router runs what the screen that is on screen registered, and says "not handled" where nothing is. */
class AppCommandRouterTest {
    private val scope = TestScope(UnconfinedTestDispatcher())
    private var locks = 0
    private var fallbacks = 0
    private var fallbackResult = true
    private val router = AppCommandRouter(
        scope = scope,
        lockApp = { locks++ },
        newNoteFallback = {
            fallbacks++
            fallbackResult
        },
    )

    private class RecordingEditor(var allowTrash: Boolean = true, var findOpen: Boolean = true) {
        val calls = mutableListOf<String>()
        val commands = EditorCommands(
            find = { true.also { calls += "find" } },
            moveToTrash = { allowTrash.also { calls += "trash" } },
            dismiss = { findOpen.also { calls += "dismiss" } },
        )
    }

    @Test
    fun aNewNoteIsStartedByTheHomeScreenWhenItIsThere() {
        var started = 0
        router.attachNewNote { started++ }

        assertTrue(router.run(AppCommand.NewNote))

        assertEquals(1, started)
        assertEquals(0, fallbacks)
    }

    @Test
    fun withoutAHomeScreenTheFallbackStartsTheNote() {
        assertTrue(router.run(AppCommand.NewNote))
        assertEquals(1, fallbacks)

        fallbackResult = false
        assertFalse(router.run(AppCommand.NewNote))
    }

    @Test
    fun aHomeScreenThatLeftNoLongerStartsNotes() {
        val action: () -> Unit = { error("left") }
        router.attachNewNote(action)
        router.detachNewNote(action)

        router.run(AppCommand.NewNote)

        assertEquals(1, fallbacks)
    }

    @Test
    fun aNewerHomeScreenIsNotReplacedByTheOldOnesLeaving() {
        var older = 0
        var newer = 0
        val first: () -> Unit = { older++ }
        val second: () -> Unit = { newer++ }
        router.attachNewNote(first)
        router.attachNewNote(second)
        router.detachNewNote(first)

        router.run(AppCommand.NewNote)

        assertEquals(0, older)
        assertEquals(1, newer)
    }

    @Test
    fun lockingIsAlwaysDone() {
        assertTrue(router.run(AppCommand.LockApp))
        assertEquals(1, locks)
    }

    @Test
    fun theEditorCommandsReachTheEditorOnScreen() {
        val editor = RecordingEditor()
        router.attachEditor(editor.commands)

        assertTrue(router.run(AppCommand.FindInNote))
        assertTrue(router.run(AppCommand.DeleteNote))
        assertTrue(router.run(AppCommand.Dismiss))

        assertEquals(listOf("find", "trash", "dismiss"), editor.calls)
    }

    @Test
    fun anEditorThatDoesNothingLeavesTheKeyUnused() {
        val editor = RecordingEditor(allowTrash = false, findOpen = false)
        router.attachEditor(editor.commands)

        assertFalse(router.run(AppCommand.DeleteNote))
        assertFalse(router.run(AppCommand.Dismiss))
    }

    @Test
    fun withoutAnEditorTheNoteCommandsAreNotHandled() {
        assertFalse(router.run(AppCommand.FindInNote))
        assertFalse(router.run(AppCommand.DeleteNote))
        assertFalse(router.run(AppCommand.Dismiss))
    }

    @Test
    fun anEditorThatLeftIsForgottenButANewerOneStays() {
        val older = RecordingEditor()
        val newer = RecordingEditor()
        router.attachEditor(older.commands)
        router.attachEditor(newer.commands)
        router.detachEditor(older.commands)

        router.run(AppCommand.FindInNote)

        assertEquals(emptyList(), older.calls)
        assertEquals(listOf("find"), newer.calls)

        router.detachEditor(newer.commands)
        assertFalse(router.run(AppCommand.FindInNote))
    }

    @Test
    fun theTabCommandsActOnTheTabsOfThePane() = runTest {
        val tabs = NoteTabs()
        tabs.open(1)
        tabs.open(2)
        tabs.open(3)
        router.attachTabs(tabs)

        assertTrue(router.run(AppCommand.NextTab))
        assertEquals(1L, tabs.active?.noteId)
        assertTrue(router.run(AppCommand.PreviousTab))
        assertEquals(3L, tabs.active?.noteId)
        assertTrue(router.run(AppCommand.CloseTab))
        assertEquals(listOf(1L, 2L), tabs.tabs.map { it.noteId })
    }

    @Test
    fun closingTheShownTabAsksItsEditorToCloseItself() = runTest {
        val tabs = NoteTabs()
        var closes = 0
        tabs.attach(ActiveEditor(save = { true }, requestClose = { closes++ }))
        tabs.open(1)
        router.attachTabs(tabs)

        assertTrue(router.run(AppCommand.CloseTab))

        assertEquals(1, closes)
        assertEquals(1, tabs.tabs.size)
    }

    @Test
    fun withoutTabsOrWithJustOneTheTabKeysAreNotHandled() = runTest {
        assertFalse(router.run(AppCommand.CloseTab))
        assertFalse(router.run(AppCommand.NextTab))
        assertFalse(router.run(AppCommand.PreviousTab))

        val tabs = NoteTabs()
        router.attachTabs(tabs)
        assertFalse(router.run(AppCommand.CloseTab))
        tabs.open(1)
        assertFalse(router.run(AppCommand.NextTab))
        assertFalse(router.run(AppCommand.PreviousTab))
    }

    @Test
    fun tabsThatLeftAreForgottenButNewerOnesStay() = runTest {
        val older = NoteTabs().also { it.open(1) }
        val newer = NoteTabs().also { it.open(2) }
        router.attachTabs(older)
        router.attachTabs(newer)
        router.detachTabs(older)

        assertTrue(router.run(AppCommand.CloseTab))
        assertTrue(newer.tabs.isEmpty())
        assertEquals(1, older.tabs.size)

        router.detachTabs(newer)
        assertFalse(router.run(AppCommand.CloseTab))
    }
}
