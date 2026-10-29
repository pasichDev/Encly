package com.pasich.encly.desktop

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.pasich.encly.desktop.di.DesktopAppGraph
import com.pasich.encly.desktop.security.DesktopAppLifecycle
import com.pasich.encly.desktop.storage.DesktopOs
import com.pasich.encly.desktop.window.DesktopShortcuts
import com.pasich.encly.desktop.window.DesktopWindows
import com.pasich.encly.desktop.window.WindowForeground
import com.pasich.encly.desktop.window.WindowFrames
import com.pasich.encly.desktop.window.WindowServices
import com.pasich.encly.desktop.window.awaitPendingWork
import com.pasich.encly.presentation.designsystem.AppCommand
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.Frame
import java.awt.GraphicsEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopWindowsTest {
    @get:Rule
    val temp = TemporaryFolder()

    private var canOpen = true
    private var ended = 0

    private fun windows(): DesktopWindows {
        val graph = createGraph()
        return DesktopWindows(
            canOpen = { canOpen },
            onLastClosed = { ended++ },
            create = { id, owner ->
                WindowServices.create(
                    graph.environment,
                    graph.lifecycle,
                    claims = owner.registry.forWindow(id),
                )
            },
        )
    }

    private fun createGraph() =
        createGraphFactory<DesktopAppGraph.Factory>().create(testEnvironment(temp.root.toPath()))

    @Test
    fun theFirstWindowOpensAlwaysAndTheFocusIsOnIt() {
        canOpen = false
        val windows = windows()

        val first = assertNotNull(windows.open())

        assertEquals(listOf(first.id), windows.sessions.map { it.id })
        assertSame(first, windows.focused)
    }

    @Test
    fun furtherWindowsNeedAVaultThatAllowsThem() {
        val windows = windows()
        windows.open()

        canOpen = false
        assertNull(windows.open())
        assertEquals(1, windows.sessions.size)

        canOpen = true
        assertNotNull(windows.open())
        assertEquals(2, windows.sessions.size)
    }

    @Test
    fun aWindowOpenedOnANoteKnowsIt() {
        val windows = windows()
        windows.open()

        val second = assertNotNull(windows.open(noteId = 42L))

        assertEquals(42L, second.openNoteId)
    }

    @Test
    fun everyWindowHasItsOwnRequestsDropsAndPickers() {
        val windows = windows()
        val first = assertNotNull(windows.open())
        val second = assertNotNull(windows.open())

        assertNotSame(first.services.navigation, second.services.navigation)
        assertNotSame(first.services.dropRouter, second.services.dropRouter)
        assertNotSame(first.services.documentPickers, second.services.documentPickers)

        // A request for one window is never taken by the other.
        first.services.navigation.request(AppCommand.NewNote)
        assertEquals(listOf(AppCommand.NewNote), drainCommands(first.services.navigation))
        assertEquals(emptyList(), drainCommands(second.services.navigation))
    }

    @Test
    fun theMenuAndTheOsReachTheWindowThatWasActiveLast() {
        val windows = windows()
        val first = assertNotNull(windows.open())
        val second = assertNotNull(windows.open())
        val firstRig = CommandsRig()
        val secondRig = CommandsRig()
        first.commands = firstRig.commands
        second.commands = secondRig.commands

        // The newest window has the focus.
        assertSame(second, windows.focused)
        windows.focused?.commands?.settings()
        assertEquals(listOf("SettingsRoute"), secondRig.requested())
        assertEquals(emptyList(), firstRig.requested())

        windows.focus(first.id)
        windows.focused?.commands?.about()
        assertEquals(listOf("AboutRoute"), firstRig.requested())
        assertEquals(emptyList(), secondRig.requested())
    }

    @Test
    fun focusingAWindowThatIsGoneChangesNothing() {
        val windows = windows()
        val first = assertNotNull(windows.open())
        windows.focus(99)
        assertSame(first, windows.focused)
        assertFalse(windows.isOpen(99))
        assertTrue(windows.isOpen(first.id))
    }

    @Test
    fun closingAWindowMovesTheFocusAndOnlyTheLastOneEndsTheApp() {
        val windows = windows()
        val first = assertNotNull(windows.open())
        val second = assertNotNull(windows.open())

        windows.close(second.id)
        assertEquals(0, ended)
        assertSame(first, windows.focused)

        // Closing what is already closed does nothing.
        windows.close(second.id)
        assertEquals(0, ended)

        windows.close(first.id)
        assertEquals(1, ended)
        assertNull(windows.focused)
    }

    @Test
    fun quitClosesEveryWindowAndEndsTheAppOnce() {
        val windows = windows()
        windows.open()
        windows.open()

        windows.closeAll()
        windows.closeAll()

        assertEquals(0, windows.sessions.size)
        assertEquals(1, ended)
    }

    @Test
    fun theWindowsShareOneNoteRegistryButClaimPerWindow() {
        val windows = windows()
        val first = assertNotNull(windows.open())
        val second = assertNotNull(windows.open())

        assertTrue(assertNotNull(first.services.claims).claim(5))
        assertFalse(assertNotNull(second.services.claims).claim(5))
        assertTrue(windows.registry.isClaimed(5))
    }
}

class WindowForegroundTest {
    private class Stops : DefaultLifecycleObserver {
        var stopCount = 0

        override fun onStop(owner: LifecycleOwner) {
            stopCount++
        }
    }

    private val lifecycle = DesktopAppLifecycle()
    private val observer = Stops().also { lifecycle.lifecycle.addObserver(it) }
    private val foreground = WindowForeground(lifecycle)

    private fun twoWindowsOpen() {
        foreground.opened(1)
        foreground.activated(1)
        foreground.opened(2)
    }

    @Test
    fun theFocusMovingBetweenOurOwnWindowsIsNotLeavingTheApp() {
        twoWindowsOpen()
        val stopsBefore = observer.stopCount

        // The OS reports the loss of one window first and names the other as its opposite.
        foreground.deactivated(1, toOwnWindow = true)
        foreground.activated(2)
        foreground.deactivated(2, toOwnWindow = true)
        foreground.activated(1)

        assertEquals(stopsBefore, observer.stopCount)
        assertTrue(lifecycle.inForeground)
    }

    @Test
    fun leavingForAnotherApplicationStopsAndComingBackStarts() {
        twoWindowsOpen()

        foreground.deactivated(1, toOwnWindow = false)
        assertFalse(lifecycle.inForeground)
        assertEquals(1, observer.stopCount)

        foreground.activated(2)
        assertTrue(lifecycle.inForeground)
    }

    @Test
    fun theAppStaysInFrontWhileAnyWindowIsActive() {
        twoWindowsOpen()
        foreground.activated(2)

        foreground.deactivated(1, toOwnWindow = false)

        assertTrue(lifecycle.inForeground, "window 2 is still active")
        foreground.deactivated(2, toOwnWindow = false)
        assertFalse(lifecycle.inForeground)
    }

    @Test
    fun theAppIsLeftOnlyWhenEveryWindowIsMinimised() {
        twoWindowsOpen()
        foreground.activated(2)

        foreground.iconified(1)
        assertTrue(lifecycle.inForeground, "window 2 is not minimised")

        foreground.iconified(2)
        assertFalse(lifecycle.inForeground)

        foreground.deiconified(2)
        assertTrue(lifecycle.inForeground)
    }

    @Test
    fun closingAWindowIsNeverReportedAsLeavingTheApp() {
        twoWindowsOpen()
        foreground.activated(2)
        val stopsBefore = observer.stopCount

        foreground.closed(2)
        assertEquals(stopsBefore, observer.stopCount)

        // The last one too: the vault is closed after the editors saved, not under them.
        foreground.closed(1)
        assertEquals(stopsBefore, observer.stopCount)
        assertTrue(lifecycle.inForeground)
    }

    @Test
    fun aMinimisedWindowThatIsClosedNoLongerHoldsTheOthersBack() {
        twoWindowsOpen()
        foreground.iconified(1)
        foreground.activated(2)

        foreground.closed(1)

        assertTrue(lifecycle.inForeground)
    }
}

class PendingWorkTest {
    @Test
    fun waitsForWhatTheScopeLaunched() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val gate = CompletableDeferred<Unit>()
        var saved = false
        scope.launch {
            gate.await()
            saved = true
        }
        scope.launch { gate.complete(Unit) }

        scope.awaitPendingWork(timeoutMillis = 5_000)

        assertTrue(saved)
    }

    @Test
    fun givesUpAfterTheTimeout() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch { CompletableDeferred<Unit>().await() }

        val started = System.nanoTime()
        scope.awaitPendingWork(timeoutMillis = 100)

        assertTrue((System.nanoTime() - started) / 1_000_000 < 5_000)
    }

    @Test
    fun aScopeWithoutAJobHasNothingToWaitFor() = runBlocking {
        CoroutineScope(Dispatchers.Default).awaitPendingWork(timeoutMillis = 100)
    }
}

class WindowShortcutsTest {
    @Test
    fun newWindowPrintAndPdfAreMenuChordsWithTheirOwnKeys() {
        for (os in DesktopOs.entries) {
            val chords = DesktopShortcuts.menuOnly(os)
            assertTrue(DesktopShortcuts.newWindow(os) in chords)
            assertTrue(DesktopShortcuts.printNote(os) in chords)
            assertTrue(DesktopShortcuts.exportPdf(os) in chords)
            assertEquals(chords.size, chords.toSet().size, "$os: a chord is used twice")
            assertTrue(DesktopShortcuts.newWindow(os).shift)
            assertFalse(DesktopShortcuts.printNote(os).shift)
            assertTrue(DesktopShortcuts.exportPdf(os).shift)
        }
    }
}

class WindowFramesTest {
    @Test
    fun theFocusLeavingTheAppIsNoFrameOfTheApp() {
        assertFalse(null in WindowFrames())
    }

    @Test
    fun framesAreRememberedUntilTheyAreRemoved() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        val frames = WindowFrames()
        val frame = Frame()
        val other = Frame()
        try {
            frames.add(frame)

            assertTrue(frame in frames)
            assertFalse(other in frames)
            frames.remove(frame)
            assertFalse(frame in frames)
        } finally {
            frame.dispose()
            other.dispose()
        }
    }
}
