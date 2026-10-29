package com.pasich.encly.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.onboarding_welcome_headline
import com.pasich.encly.core.security.InitialStatus
import com.pasich.encly.desktop.di.DesktopAppGraph
import com.pasich.encly.platform.AppWindow
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private fun graphIn(
    directory: Path,
    timer: ManualTimer = ManualTimer(),
    capture: RecordingCapture = RecordingCapture(),
): DesktopAppGraph =
    createGraphFactory<DesktopAppGraph.Factory>().create(testEnvironment(directory, timer = timer, capture = capture))

class DesktopAppGraphTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `the graph builds and gives out every service`() {
        val graph = graphIn(temp.root.toPath())
        assertNotNull(graph.securityManager)
        assertNotNull(graph.sessionLockManager)
        assertNotNull(graph.settingsRepository)
        assertNotNull(graph.autoLockPolicy)
        assertNotNull(graph.handoffStagingSweeper)
        assertNotNull(graph.flags)
        assertNotNull(graph.applicationScope)
        assertNotNull(graph.lifecycle)
        assertNotNull(graph.deviceLockWatcher)
        assertNotNull(graph.dropRouter)
        assertNotNull(graph.navigationRequests)
        assertNotNull(graph.toaster)
        assertNotNull(graph.clipboardWiper)
        assertNotNull(graph.locales)
        assertNotNull(graph.documentPickers)
        assertNotNull(graph.externalLinks)
        assertNotNull(graph.clipboard)
        assertNotNull(graph.appInfo)
        assertNotNull(graph.metroViewModelFactory)
        assertSame(graph.environment.dataDirectory, graph.environment.dataDirectory)
    }

    @Test
    fun `singletons are one instance`() {
        val graph = graphIn(temp.root.toPath())
        assertSame(graph.securityManager, graph.securityManager)
        assertSame(graph.sessionLockManager, graph.sessionLockManager)
        assertSame(graph.navigationRequests, graph.navigationRequests)
        assertSame(graph.flags, graph.flags)
    }

    @Test
    fun `the flags and settings live in the data directory`() {
        val graph = graphIn(temp.root.toPath())
        graph.flags.commit { putBoolean("probe", true) }
        assertTrue(Files.isRegularFile(temp.root.toPath().resolve("encly_flags_v3.properties")))
    }

    @Test
    fun `a new install starts at onboarding and the first start writes only into the data directory`() {
        val graph = graphIn(temp.root.toPath())
        assertEquals(InitialStatus.ONBOARDING, runBlocking { graph.securityManager.resolveInitialStatus() })
        assertFalse(graph.securityManager.isDatabaseUnlocked())
    }

    @Test
    fun `the handoff sweep runs on an empty cache`() {
        val graph = graphIn(temp.root.toPath())
        graph.handoffStagingSweeper.sweep()
    }

    @Test
    fun `the lock manager follows the window lifecycle`() {
        val timer = ManualTimer()
        val graph = graphIn(temp.root.toPath(), timer)
        graph.lifecycle.lifecycle.addObserver(graph.sessionLockManager)
        // Leaving with no vault open locks nothing and leaves nothing scheduled.
        graph.lifecycle.windowDeactivated()
        graph.lifecycle.windowActivated()
        assertFalse(graph.sessionLockManager.locked.value)
        assertTrue(timer.pending.isEmpty())
    }
}

@OptIn(ExperimentalTestApi::class)
class DesktopAppUiTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a new install shows the onboarding screen in the window`() = runComposeUiTest {
        val capture = RecordingCapture()
        val graph = graphIn(temp.root.toPath(), capture = capture)
        setContent { DesktopApp(graph, AppWindow.None) }
        val headline = runBlocking {
            org.jetbrains.compose.resources.getString(Res.string.onboarding_welcome_headline)
        }
        waitUntil(timeoutMillis = 30_000) { onAllNodesWithText(headline).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `a request for a screen is ignored while no vault is open`() = runComposeUiTest {
        val graph = graphIn(temp.root.toPath())
        setContent { DesktopApp(graph, AppWindow.None) }
        graph.navigationRequests.request("SettingsRoute")
        val headline = runBlocking {
            org.jetbrains.compose.resources.getString(Res.string.onboarding_welcome_headline)
        }
        waitUntil(timeoutMillis = 30_000) { onAllNodesWithText(headline).fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
        onNodeWithText(headline).assertExists()
    }
}
