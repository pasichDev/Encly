package com.pasich.encly

import android.Manifest
import android.app.Application
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * An activity that is finished (its task swiped out of Recents, or Back out of the app on Android 11
 * and older) closes the open vault at once. One that is only recreated (rotation, theme, language,
 * a resized window) or destroyed by the system while stopped keeps it open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityTaskRemovalTest {
    private val security = mock(SecurityManager::class.java).also {
        `when`(it.isLockable()).thenReturn(true)
        `when`(it.isDatabaseUnlocked()).thenReturn(true)
    }
    private val sessions = SessionLockManager(security)

    @Before
    fun grantWhatTheManifestDeclares() {
        // Granted at install on a device; Robolectric grants nothing by itself.
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.HIDE_OVERLAY_WINDOWS)
    }

    /** A running activity whose session manager is [sessions], over an open, lockable vault. */
    private fun running(): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java).setup().also { it.get().sessionLockManager = sessions }

    @Test
    fun aFinishedActivityClosesTheVault() {
        val controller = running()

        // What removing the task does to its activities.
        controller.get().finish()
        controller.pause().stop().destroy()

        verify(security).lock()
        assertTrue(sessions.locked.value)
    }

    @Test
    fun aRecreatedActivityKeepsTheVaultOpen() {
        val controller = running()
        val first = controller.get()

        controller.recreate()

        try {
            assertNotSame(first, controller.get())
            verify(security, never()).lock()
            assertFalse(sessions.locked.value)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun aConfigurationChangeKeepsTheVaultOpen() {
        val controller = running()
        val first = controller.get()
        val rotated = Configuration(first.resources.configuration).apply {
            orientation = Configuration.ORIENTATION_LANDSCAPE
            screenWidthDp = first.resources.configuration.screenHeightDp
            screenHeightDp = first.resources.configuration.screenWidthDp
        }

        controller.configurationChange(rotated)

        try {
            assertNotSame("the activity handles no configuration change itself", first, controller.get())
            verify(security, never()).lock()
            assertFalse(sessions.locked.value)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun anActivityTheSystemDestroysWhileStoppedKeepsTheVaultOpen() {
        val controller = running()

        // A file picker on top, then the system takes the stopped activity back for memory.
        controller.pause().stop().destroy()

        verify(security, never()).lock()
        assertFalse(sessions.locked.value)
    }
}
