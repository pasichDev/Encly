package com.pasich.encly.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.lock_subtitle
import com.pasich.encly.core.resources.notes_title
import com.pasich.encly.core.security.AutoLock
import com.pasich.encly.core.security.AutoLockDelay
import com.pasich.encly.core.security.VaultUnlockResult
import com.pasich.encly.desktop.di.DesktopAppGraph
import com.pasich.encly.desktop.window.DesktopWindows
import com.pasich.encly.desktop.window.WindowForeground
import com.pasich.encly.desktop.window.WindowServices
import com.pasich.encly.platform.AppWindow
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PIN = "123456"

/** A graph over a real vault that onboarding has committed and left open, as after the first PIN. */
private fun committedGraph(directory: java.nio.file.Path): DesktopAppGraph {
    val graph = createGraphFactory<DesktopAppGraph.Factory>().create(testEnvironment(directory))
    val security = graph.securityManager
    check(security.initializeNewVault(null))
    check(security.configurePin(PIN.toCharArray()))
    check(security.finishInitialSetup())
    graph.lifecycle.lifecycle.addObserver(graph.sessionLockManager)
    return graph
}

/**
 * Two windows over one vault and one lock, as the host runs them: each its own [WindowServices] and `DesktopApp`,
 * the session and the lock the graph's.
 */
@OptIn(ExperimentalTestApi::class)
class MultiWindowLockTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun lockingInOneWindowLocksBothAndUnlockingInOneOpensBothAndNoWindowShowsContentWhileLocked() = runComposeUiTest {
        val graph = committedGraph(temp.root.toPath())
        val first = WindowServices.create(graph.environment, graph.lifecycle)
        val second = WindowServices.create(graph.environment, graph.lifecycle)
        val lockPrompt = runBlocking { getString(Res.string.lock_subtitle) }
        val notes = runBlocking { getString(Res.string.notes_title) }
        fun prompts() = onAllNodesWithText(lockPrompt).fetchSemanticsNodes().size
        fun lists() = onAllNodesWithText(notes).fetchSemanticsNodes().size

        setContent {
            Row {
                Box(Modifier.weight(1f).fillMaxHeight()) { DesktopApp(graph, AppWindow.None, services = first) }
                Box(Modifier.weight(1f).fillMaxHeight()) { DesktopApp(graph, AppWindow.None, services = second) }
            }
        }

        // Both windows open on the notes.
        waitUntil(timeoutMillis = 60_000) { lists() >= 2 }
        assertEquals(0, prompts())

        // The lock (idle, the Lock command or the OS) is the process's: both windows go to the lock screen.
        graph.sessionLockManager.lockNow()
        waitUntil(timeoutMillis = 60_000) { prompts() == 2 }
        assertEquals(0, lists(), "no window shows notes while the vault is locked")
        assertFalse(graph.securityManager.isDatabaseUnlocked())

        // One unlock (whichever lock screen it was typed on) opens the vault for both.
        assertEquals(VaultUnlockResult.SUCCESS, graph.securityManager.unlockWithPin(PIN.toCharArray()))
        assertTrue(graph.sessionLockManager.onUnlocked())
        waitUntil(timeoutMillis = 60_000) { prompts() == 0 }
        waitUntil(timeoutMillis = 60_000) { lists() >= 2 }
        assertTrue(graph.securityManager.isDatabaseUnlocked())
    }
}

class MultiWindowForegroundTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun movingTheFocusBetweenWindowsKeepsTheVaultOpenButLeavingTheAppLocksIt() {
        val graph = committedGraph(temp.root.toPath())
        (graph.autoLockPolicy as AutoLock).setDelay(AutoLockDelay.IMMEDIATELY)
        val foreground = WindowForeground(graph.lifecycle)
        foreground.opened(1)
        foreground.activated(1)
        foreground.opened(2)

        // The OS reports window 1 losing the focus before window 2 gets it.
        foreground.deactivated(1, toOwnWindow = true)
        assertFalse(graph.sessionLockManager.locked.value, "the app was not left")
        foreground.activated(2)
        foreground.deactivated(2, toOwnWindow = true)
        foreground.activated(1)
        assertFalse(graph.sessionLockManager.locked.value)
        assertTrue(graph.securityManager.isDatabaseUnlocked())

        // The user goes to another application: with "immediately" the vault locks.
        foreground.deactivated(1, toOwnWindow = false)
        assertTrue(graph.sessionLockManager.locked.value)
        assertFalse(graph.securityManager.isDatabaseUnlocked())
    }

    @Test
    fun withoutTheHandoverTheSameFocusChangeWouldLockTheVault() {
        // What a single lifecycle fed by every window's events would do: the reason for the aggregation.
        val graph = committedGraph(temp.root.toPath())
        (graph.autoLockPolicy as AutoLock).setDelay(AutoLockDelay.IMMEDIATELY)

        graph.lifecycle.windowDeactivated()

        assertTrue(graph.sessionLockManager.locked.value)
    }

    @Test
    fun closingAWindowDoesNotLockTheVaultUnderTheOthers() {
        val graph = committedGraph(temp.root.toPath())
        (graph.autoLockPolicy as AutoLock).setDelay(AutoLockDelay.IMMEDIATELY)
        val foreground = WindowForeground(graph.lifecycle)
        foreground.opened(1)
        foreground.activated(1)
        foreground.opened(2)

        foreground.closed(1)

        assertFalse(graph.sessionLockManager.locked.value)
        assertTrue(graph.securityManager.isDatabaseUnlocked())
    }
}

class NewWindowAvailabilityTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun windowsOver(graph: DesktopAppGraph) = DesktopWindows(
        canOpen = graph.securityManager::isLockable,
        onLastClosed = {},
        create = { _, _ -> WindowServices.create(graph.environment, graph.lifecycle) },
    )

    @Test
    fun aFurtherWindowIsRefusedDuringOnboardingAndAllowedOnceTheVaultExists() {
        val fresh = createGraphFactory<DesktopAppGraph.Factory>().create(testEnvironment(temp.newFolder().toPath()))
        val onboarding = windowsOver(fresh)
        assertTrue(onboarding.open() != null, "the first window is the app itself")
        assertNull(onboarding.open(), "no vault yet: onboarding is one window")

        val committed = windowsOver(committedGraph(temp.newFolder().toPath()))
        assertTrue(committed.open() != null)
        assertTrue(committed.open() != null)
        assertEquals(2, committed.sessions.size)
    }
}
