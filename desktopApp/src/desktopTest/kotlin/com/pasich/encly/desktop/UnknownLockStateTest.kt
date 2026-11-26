package com.pasich.encly.desktop

import com.pasich.encly.desktop.security.DesktopDeviceLockWatcher
import com.pasich.encly.desktop.security.LinuxLiveLock
import com.pasich.encly.desktop.security.OsLockSignals
import com.pasich.encly.desktop.security.screenSaverSource
import org.freedesktop.dbus.errors.ServiceUnknown
import org.freedesktop.dbus.exceptions.DBusExecutionException
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

    private var now = 0L

    private fun watcher(signals: OsLockSignals) =
        DesktopDeviceLockWatcher(signals, { now }) { it() }.also { it.start() }

    /** What dbus-java throws for a question about a name nobody owns (`GetNameOwner`): an error of the bus's. */
    private fun noOwner() = DBusExecutionException("The name org.gnome.ScreenSaver has no owner")

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
    fun aScreenSaverNobodyOwnsIsNotThereAndIsNeverRead() {
        // What the real connection does for a name nobody owns when it is read: a plain error with no name of its own.
        var read = 0
        val absent = screenSaverSource(hasOwner = { false }) {
            read++
            throw noOwner()
        }

        assertEquals(false, LinuxLiveLock({ false }, listOf(absent)).state())
        assertFalse(LinuxLiveLock({ false }, listOf(absent)).isLocked())
        // The case of sway, i3 and Hyprland: logind says "not locked" and all four savers are unowned.
        assertEquals(false, LinuxLiveLock({ false }, List(4) { absent }).state())
        assertEquals(true, LinuxLiveLock({ true }, List(4) { absent }).state())
        assertEquals(true, LinuxLiveLock({ false }, listOf(absent, screenSaverSource({ true }) { true })).state())
        assertEquals(0, read)
    }

    @Test
    fun theUnownedNameErrorOfTheBusIsUnknownWhenReadAnywayWhichIsWhyItIsNotRead() {
        // Pins what the library does, so that nobody "simplifies" the owner question away: no class, no type to tell.
        assertEquals(DBusExecutionException::class.java, noOwner()::class.java)
        assertEquals(DBusExecutionException::class.java.name, noOwner().type)
        assertNull(LinuxLiveLock({ false }, listOf({ throw noOwner() })).state())
    }

    @Test
    fun aSourceThatIsThereAndFailsIsStillUnknownAndOneThatStoppedIsGone() {
        val failing = screenSaverSource({ true }) { throw DBusExecutionException("timed out") }
        assertNull(LinuxLiveLock({ false }, listOf(failing)).state())
        assertNull(LinuxLiveLock({ false }, listOf({ throw java.util.concurrent.TimeoutException() })).state())
        assertEquals(false, LinuxLiveLock({ false }, listOf({ throw ServiceUnknown("gone") })).state())
        assertEquals(true, LinuxLiveLock({ false }, listOf(failing, { true })).state())
    }

    // A "locked" that only a signal set and the OS can never confirm must not refuse every unlock for good.

    @Test
    fun aLockedStateNothingConfirmsIsDroppedAfterRepeatedUnansweredQuestionsOverAWhile() {
        val signals = Asked(live = null)
        val watcher = watcher(signals)
        signals.report!!(true)

        // A burst of questions (one unlock asks twice) is not enough, and neither is a long gap with one question.
        repeat(5) { assertTrue(watcher.isDeviceLocked()) }
        now = 29_999
        assertTrue(watcher.isDeviceLocked())
        // Enough questions, over enough time: the stale "locked" goes.
        now = 30_000
        assertFalse(watcher.isDeviceLocked())
        assertFalse(watcher.isDeviceLocked())
    }

    @Test
    fun aRealAnswerOrAFreshLockReportRestartsTheCount() {
        val signals = Asked(live = null)
        val watcher = watcher(signals)
        signals.report!!(true)
        repeat(3) { assertTrue(watcher.isDeviceLocked()) }

        // The OS answers once (it can be asked after all): the count of unanswered questions starts again.
        signals.live = true
        assertTrue(watcher.isDeviceLocked())
        signals.live = null
        now = 40_000
        assertTrue(watcher.isDeviceLocked())

        // A new report of a lock is new evidence: it too starts the count again.
        now = 80_000
        signals.report!!(true)
        repeat(2) { assertTrue(watcher.isDeviceLocked()) }
        now = 100_000
        assertTrue(watcher.isDeviceLocked())
        now = 110_000
        assertFalse(watcher.isDeviceLocked())
    }

    @Test
    fun aRealLockStaysLockedWhileTheOsConfirmsIt() {
        val signals = Asked(live = true)
        val watcher = watcher(signals)
        signals.report!!(true)

        repeat(50) {
            now += 10_000
            assertTrue(watcher.isDeviceLocked())
        }
    }

    @Test
    fun aDroppedLockIsLockedAgainByTheNextReportOfOne() {
        val signals = Asked(live = null)
        val watcher = watcher(signals)
        var locks = 0
        watcher.onLocked { locks++ }
        signals.report!!(true)
        repeat(3) { watcher.isDeviceLocked() }
        now = 31_000
        assertFalse(watcher.isDeviceLocked())

        signals.report!!(true)

        assertTrue(watcher.isDeviceLocked())
        assertEquals(2, locks)
    }

    @Test
    fun theOlderBooleanAnswerIsUnchanged() {
        assertFalse(LinuxLiveLock({ error("no bus") }, listOf({ error("no saver") })).isLocked())
    }
}
