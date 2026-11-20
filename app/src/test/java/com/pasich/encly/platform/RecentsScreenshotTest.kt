package com.pasich.encly.platform

import android.app.Activity
import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** An activity that records what it was told about the task switcher thumbnail (Android 13+). */
class RecentsRecordingActivity : Activity() {
    val recentsCalls = mutableListOf<Boolean>()

    override fun setRecentsScreenshotEnabled(enabled: Boolean) {
        recentsCalls += enabled
    }
}

/**
 * The task switcher shows no thumbnail of a window that may be captured (issue #16): while the user allows
 * screenshots, the recents snapshot is switched off (`setRecentsScreenshotEnabled(false)`, Android 13+), and
 * back on once the allowance ends. A protected window needs no call: FLAG_SECURE already blanks its thumbnail.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RecentsScreenshotTest {
    private fun window(): Pair<RecentsRecordingActivity, AndroidAppWindow> {
        val activity = Robolectric.buildActivity(RecentsRecordingActivity::class.java).setup().get()
        return activity to AndroidAppWindow(activity, activity.window.decorView)
    }

    @Test
    fun allowingScreenshotsSwitchesTheRecentsThumbnailOff() {
        val (activity, window) = window()
        window.enforceSecure()
        assertTrue("a protected window needs no call", activity.recentsCalls.isEmpty())

        window.setCaptureAllowed(true)

        assertEquals(listOf(false), activity.recentsCalls)
    }

    @Test
    fun theThumbnailStaysOffWhileTheWindowLeavesAndReturnsToTheForeground() {
        val (activity, window) = window()

        window.setCaptureAllowed(true)
        window.setInForeground(true)
        window.setInForeground(false)
        window.setInForeground(true)
        window.enforceSecure()

        assertEquals("one call, not one per frame or per lifecycle step", listOf(false), activity.recentsCalls)
    }

    @Test
    fun endingTheAllowanceSwitchesTheThumbnailBackToTheDefault() {
        val (activity, window) = window()
        window.setCaptureAllowed(true)

        window.setCaptureAllowed(false)

        assertEquals(listOf(false, true), activity.recentsCalls)
    }

    @Test
    @Config(sdk = [32])
    fun beforeAndroid13TheCallIsNeverMade() {
        val (activity, window) = window()

        window.setCaptureAllowed(true)
        window.setCaptureAllowed(false)

        assertTrue(activity.recentsCalls.isEmpty())
    }
}
