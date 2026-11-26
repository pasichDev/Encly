package com.pasich.encly.desktop

import com.pasich.encly.desktop.security.DesktopDeviceLockWatcher
import com.pasich.encly.desktop.security.LinuxLiveLock
import com.pasich.encly.desktop.security.OsLockSignals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A read of the OS lock state that fails or times out is unknown, never "unlocked". */
class UnknownLockStateTest {
    private class Asked(var live: Boolean?) : OsLockSignals {
        var report: ((Boolean) -> Unit)? = null

        override fun start(onChange: (locked: Boolean) -> Unit): AutoCloseable {
            report = onChange
            return AutoCloseable { }
        }

        override fun liveLocked(): Boolean? = live

        override val asksTheOs: Boolean get() = true
    }

    private fun watcher(signals: OsLockSignals) = DesktopDeviceLockWatcher(signals) { it() }.also { it.start() }

    @Test
    fun anAnswerThatDidNotComeKeepsAKnownLockInsteadOfOverridingItWithUnlocked() {
        val signals = Asked(live = null)
        val watcher = watcher(signals)
        signals.report!!(true)

        assertTrue(watcher.isDeviceLocked())

        signals.live = false
        assertFalse(watcher.isDeviceLocked(), "a real answer still wins over the last report")
        signals.live = null
        assertTrue(watcher.isDeviceLocked(), "unknown: the last report (locked) stands")
    }

    @Test
    fun anAnswerThatDidNotComeAfterAnUnlockReportIsTheLastReport() {
        val signals = Asked(live = null)
        val watcher = watcher(signals)
        signals.report!!(true)
        signals.report!!(false)

        assertFalse(watcher.isDeviceLocked())
    }

    @Test
    fun aSourceThatThrowsIsUnknownUnlessAnotherSaysLocked() {
        assertNull(LinuxLiveLock({ error("no bus") }, listOf({ false })).state())
        assertNull(LinuxLiveLock({ false }, listOf({ error("no saver") })).state())
        assertEquals(true, LinuxLiveLock({ error("no bus") }, listOf({ true })).state())
        assertEquals(true, LinuxLiveLock({ true }, listOf({ error("no saver") })).state())
    }

    @Test
    fun sourcesThatAreNotThereAreNotLockedAndAreNotUnknown() {
        assertEquals(false, LinuxLiveLock({ null }, listOf({ null })).state())
        assertEquals(false, LinuxLiveLock({ false }, emptyList()).state())
        assertEquals(true, LinuxLiveLock({ null }, listOf({ true })).state())
    }

    @Test
    fun theOlderBooleanAnswerIsUnchanged() {
        assertFalse(LinuxLiveLock({ error("no bus") }, listOf({ error("no saver") })).isLocked())
    }
}
