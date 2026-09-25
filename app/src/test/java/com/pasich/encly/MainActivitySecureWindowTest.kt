package com.pasich.encly

import android.Manifest
import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Every window of the app is FLAG_SECURE (no screenshots, recordings, casting or recents
 * thumbnails of notes), from onCreate on, before any frame, and again for the new instance
 * after a recreation (rotation, language or theme change).
 *
 * The flags are read the moment onCreate returns (onActivityPostCreated), before the window is
 * attached and Compose runs: AppTheme sets the flag again later, and must not hide a missing one
 * in onCreate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivitySecureWindowTest {
    private val application: Application get() = ApplicationProvider.getApplicationContext()

    /** The window flags of each MainActivity instance right after its onCreate. */
    private val flagsAfterCreate = mutableListOf<Pair<Activity, Int>>()

    private val recorder = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity is MainActivity) flagsAfterCreate += activity to activity.window.attributes.flags
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    @Before
    fun setUp() {
        // Declared in the manifest and granted at install; Robolectric grants nothing by itself.
        shadowOf(application).grantPermissions(Manifest.permission.HIDE_OVERLAY_WINDOWS)
        application.registerActivityLifecycleCallbacks(recorder)
    }

    @After
    fun tearDown() {
        application.unregisterActivityLifecycleCallbacks(recorder)
    }

    @Test
    fun theWindowIsSecureRightAfterOnCreate() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            assertEquals(1, flagsAfterCreate.size)
            assertSecure(flagsAfterCreate.single().second)
            assertSecure(controller.get().window.attributes.flags)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun theWindowIsSecureAgainAfterARecreation() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val first = controller.get()

            controller.recreate()

            val second = controller.get()
            assertNotSame(first, second)
            assertEquals(listOf(first, second), flagsAfterCreate.map { it.first })
            flagsAfterCreate.forEach { (_, flags) -> assertSecure(flags) }
            assertSecure(second.window.attributes.flags)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun assertSecure(flags: Int) {
        assertTrue(
            "FLAG_SECURE missing from the window flags ${Integer.toHexString(flags)}",
            flags and WindowManager.LayoutParams.FLAG_SECURE != 0,
        )
    }
}
