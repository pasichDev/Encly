package com.pasich.encly.desktop

import com.pasich.encly.desktop.nativeos.NativeBootClock
import com.pasich.encly.desktop.nativeos.NativeCaptureExclusion
import com.pasich.encly.desktop.nativeos.NativeScreenLock
import com.pasich.encly.desktop.storage.DesktopOs
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The native readers against the OS this test runs on (each OS runs its own in CI). They are JNA and D-Bus glue,
 * excluded from the coverage gate, but they must at least load and answer sensibly.
 */
class NativeOsTest {
    private val os = DesktopOs.current()

    @Test
    fun `the boot clock reads the uptime of this machine`() {
        val clock = NativeBootClock.forOs(os)
        val first = assertNotNull(clock.uptimeMillis(), "uptime of $os")
        assertTrue(first > 0)
        Thread.sleep(20)
        assertTrue(clock.uptimeMillis()!! >= first)
        if (os != DesktopOs.WINDOWS) assertNotNull(clock.bootId(), "boot id of $os")
    }

    @Test
    fun `the screen lock source starts and stops without a locked session`() {
        val states = mutableListOf<Boolean>()
        val subscription = NativeScreenLock.forOs(os).start { states += it }
        Thread.sleep(300)
        subscription.close()
        // A CI machine or a developer at the keyboard has an unlocked session.
        assertFalse(states.any { it }, "reported locked: $states")
    }

    @Test
    fun `the capture exclusion is available where the OS has one`() {
        val capture = NativeCaptureExclusion.forOs(os)
        if (os == DesktopOs.LINUX) assertFalse(capture.supported) else assertTrue(capture.supported)
    }
}
