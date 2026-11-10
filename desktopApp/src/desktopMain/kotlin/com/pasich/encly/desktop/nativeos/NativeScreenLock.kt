@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "TooGenericExceptionCaught", "ReturnCount")

package com.pasich.encly.desktop.nativeos

import com.pasich.encly.desktop.security.LinuxLiveLock
import com.pasich.encly.desktop.security.OsLockSignals
import com.pasich.encly.desktop.security.PollingLockSignals
import com.pasich.encly.desktop.security.SleepDetector
import com.pasich.encly.desktop.storage.DesktopOs
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.LongByReference
import com.sun.jna.win32.StdCallLibrary
import org.freedesktop.dbus.DBusMatchRule
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.DBusSigHandler
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.messages.DBusSignal
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// What the OS says about the screen being locked, per OS. JNA and D-Bus glue: it only runs against the real OS
// and is excluded from the coverage gate (PollingLockSignals and DesktopDeviceLockWatcher, which use it, are
// tested with fakes).

/** The lock signals of [os]. Never throws: a source that cannot start reports nothing. */
object NativeScreenLock {
    fun forOs(os: DesktopOs): OsLockSignals = when (os) {
        DesktopOs.MACOS -> PollingLockSignals(
            POLL_MILLIS,
            MacScreenLockProbe()::isLocked,
            SleepDetector(MacSleepClocks()::read)::sleptSinceLastCheck,
        )

        DesktopOs.WINDOWS -> PollingLockSignals(
            POLL_MILLIS,
            WindowsScreenLockProbe()::isLocked,
            SleepDetector(WindowsSleepClocks()::read)::sleptSinceLastCheck,
        )

        DesktopOs.LINUX -> LogindLockSignals()
    }

    private const val POLL_MILLIS = 1_500L
}

private interface MacCoreGraphics : Library {
    fun CGSessionCopyCurrentDictionary(): Pointer?
}

private interface MacCoreFoundation : Library {
    fun CFStringCreateWithCString(alloc: Pointer?, cStr: String, encoding: Int): Pointer?

    fun CFDictionaryGetValue(dict: Pointer, key: Pointer): Pointer?

    fun CFBooleanGetValue(boolean: Pointer): Byte

    fun CFRelease(cf: Pointer)
}

/**
 * macOS: the window server's session dictionary has `CGSSessionScreenIsLocked = true` while the lock screen is
 * up (also after the screen saver asks for the password). Asked by polling: the distributed notification
 * `com.apple.screenIsLocked` needs an Objective-C run loop that the JVM does not have.
 */
private class MacScreenLockProbe {
    private val graphics: MacCoreGraphics? = load("CoreGraphics", MacCoreGraphics::class.java)
    private val foundation: MacCoreFoundation? = load("CoreFoundation", MacCoreFoundation::class.java)
    private val key: Pointer? = foundation?.let {
        try {
            it.CFStringCreateWithCString(null, LOCKED_KEY, UTF8)
        } catch (_: Throwable) {
            null
        }
    }

    fun isLocked(): Boolean {
        val cg = graphics ?: return false
        val cf = foundation ?: return false
        val lockedKey = key ?: return false
        return try {
            val session = cg.CGSessionCopyCurrentDictionary() ?: return false
            try {
                val value = cf.CFDictionaryGetValue(session, lockedKey)
                value != null && cf.CFBooleanGetValue(value).toInt() != 0
            } finally {
                cf.CFRelease(session)
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun <T : Library> load(name: String, type: Class<T>): T? = try {
        Native.load(name, type)
    } catch (_: Throwable) {
        null
    }

    private companion object {
        const val LOCKED_KEY = "CGSSessionScreenIsLocked"
        const val UTF8 = 0x08000100
    }
}

private interface MacTime : Library {
    fun mach_continuous_time(): Long

    fun mach_absolute_time(): Long

    fun mach_timebase_info(info: IntArray): Int
}

/** macOS: `mach_continuous_time` counts the time asleep, `mach_absolute_time` does not. */
private class MacSleepClocks {
    private val time: MacTime? = try {
        Native.load("System", MacTime::class.java)
    } catch (_: Throwable) {
        null
    }
    private val timebase: IntArray? = time?.let {
        IntArray(2).takeIf { info -> it.mach_timebase_info(info) == 0 && info[1] != 0 }
    }

    fun read(): SleepDetector.Clocks? {
        val lib = time ?: return null
        val base = timebase ?: return null
        return try {
            SleepDetector.Clocks(
                counting = lib.mach_continuous_time() * base[0] / base[1] / NANOS_PER_MILLI,
                awake = lib.mach_absolute_time() * base[0] / base[1] / NANOS_PER_MILLI,
            )
        } catch (_: Throwable) {
            null
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

private interface WindowsTime : StdCallLibrary {
    fun GetTickCount64(): Long

    fun QueryUnbiasedInterruptTime(time: LongByReference): Boolean
}

/** Windows: `GetTickCount64` counts the time asleep, `QueryUnbiasedInterruptTime` (100 ns units) does not. */
private class WindowsSleepClocks {
    private val time: WindowsTime? = try {
        Native.load("kernel32", WindowsTime::class.java)
    } catch (_: Throwable) {
        null
    }

    fun read(): SleepDetector.Clocks? {
        val lib = time ?: return null
        return try {
            val awake = LongByReference()
            if (!lib.QueryUnbiasedInterruptTime(awake)) return null
            SleepDetector.Clocks(counting = lib.GetTickCount64(), awake = awake.value / HUNDRED_NANOS_PER_MILLI)
        } catch (_: Throwable) {
            null
        }
    }

    private companion object {
        const val HUNDRED_NANOS_PER_MILLI = 10_000L
    }
}

private interface WindowsUser32 : StdCallLibrary {
    fun OpenInputDesktop(flags: Int, inherit: Boolean, access: Int): Pointer?

    fun SwitchDesktop(desktop: Pointer): Boolean

    fun CloseDesktop(desktop: Pointer): Boolean
}

/**
 * Windows: while the workstation is locked the input desktop is the secure Winlogon desktop, which an
 * application cannot open (`OpenInputDesktop` fails) or switch to. Polled instead of a WTS session
 * notification, which needs a window procedure of our own that Compose's window does not expose.
 */
private class WindowsScreenLockProbe {
    private val user32: WindowsUser32? = try {
        Native.load("user32", WindowsUser32::class.java)
    } catch (_: Throwable) {
        null
    }

    fun isLocked(): Boolean {
        val lib = user32 ?: return false
        return try {
            val desktop = lib.OpenInputDesktop(0, false, DESKTOP_SWITCHDESKTOP) ?: return true
            try {
                !lib.SwitchDesktop(desktop)
            } finally {
                lib.CloseDesktop(desktop)
            }
        } catch (_: Throwable) {
            false
        }
    }

    private companion object {
        const val DESKTOP_SWITCHDESKTOP = 0x0100
    }
}

/**
 * Linux: logind's `Lock`/`Unlock` signals of the session (`loginctl lock-session`, a lid close) and the
 * manager's `PrepareForSleep` (suspend and hibernate) on the system bus, plus the `ActiveChanged` signal of the
 * desktop screen savers on the session bus (GNOME, KDE, Cinnamon, MATE: their idle lock does not go through
 * logind). Both buses are local Unix sockets. A bus that is not there is skipped.
 *
 * The signals only say when a lock starts. Whether the session is locked *now* is asked of the OS ([liveLocked]):
 * the session's `LockedHint` and the screen savers' `GetActive`. A locker started by `Session.Lock` (swaylock,
 * hyprlock, i3lock through xss-lock) never sends `Unlock`, so a flag fed by the signals alone stays "locked" after
 * the user unlocked the desktop.
 */
private class LogindLockSignals : OsLockSignals {
    @Volatile
    private var systemBus: DBusConnection? = null

    @Volatile
    private var sessionBus: DBusConnection? = null

    private val live = LinuxLiveLock(
        lockedHint = ::readLockedHint,
        screenSaversActive = SCREEN_SAVERS.map { saver -> { readScreenSaverActive(saver) } },
    )
    private val reader: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "encly-screen-lock-read").apply { isDaemon = true }
        }
    }

    override fun liveLocked(): Boolean {
        // Bounded: a bus that does not answer must not hold up the unlock that asks.
        val answer = reader.submit(Callable { live.isLocked() })
        return try {
            answer.get(READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            answer.cancel(true)
            false
        }
    }

    private fun readLockedHint(): Boolean? {
        val connection = systemBus ?: return null
        val session = connection.getRemoteObject(LOGIND_BUS, LOGIND_SESSION_PATH, Properties::class.java)
        return session.Get<Boolean>(LOGIND_SESSION, "LockedHint")
    }

    private fun readScreenSaverActive(saver: ScreenSaver): Boolean? {
        val connection = sessionBus ?: return null
        // Only a screen saver that is running: asking a name nobody owns could start its service.
        if (connection.getDBusOwnerName(saver.iface) == null) return null
        return saver.isActive(connection)
    }

    override fun start(onChange: (locked: Boolean) -> Unit): AutoCloseable {
        val connections = listOfNotNull(
            subscribe({ DBusConnectionBuilder.forSystemBus() }) { connection ->
                systemBus = connection
                connection.on(LOGIND_SESSION, "Lock") { onChange(true) }
                connection.on(LOGIND_SESSION, "Unlock") { onChange(false) }
                connection.on(LOGIND_MANAGER, "PrepareForSleep") { signal ->
                    onChange(signal.parameters.firstOrNull() as? Boolean ?: true)
                }
            },
            subscribe({ DBusConnectionBuilder.forSessionBus() }) { connection ->
                sessionBus = connection
                SCREEN_SAVERS.map { it.iface }.forEach { iface ->
                    connection.on(iface, "ActiveChanged") { signal ->
                        onChange(signal.parameters.firstOrNull() as? Boolean ?: false)
                    }
                }
            },
        )
        return AutoCloseable {
            systemBus = null
            sessionBus = null
            connections.forEach { runCatching(it::close) }
        }
    }

    private fun subscribe(builder: () -> DBusConnectionBuilder, register: (DBusConnection) -> Unit): DBusConnection? =
        try {
            builder().withShared(false).build().also { connection ->
                try {
                    register(connection)
                } catch (e: Exception) {
                    connection.close()
                    throw e
                }
            }
        } catch (_: Exception) {
            null
        }

    private fun DBusConnection.on(iface: String, member: String, handler: (DBusSignal) -> Unit) {
        addGenericSigHandler(DBusMatchRule("signal", iface, member), DBusSigHandler<DBusSignal> { handler(it) })
    }

    private companion object {
        const val LOGIND_BUS = "org.freedesktop.login1"

        // logind's alias for the session of the calling process.
        const val LOGIND_SESSION_PATH = "/org/freedesktop/login1/session/auto"
        const val LOGIND_SESSION = "org.freedesktop.login1.Session"
        const val LOGIND_MANAGER = "org.freedesktop.login1.Manager"
        const val READ_TIMEOUT_MILLIS = 500L
        val SCREEN_SAVERS = listOf(
            screenSaver(
                "org.freedesktop.ScreenSaver",
                "/org/freedesktop/ScreenSaver",
                FreedesktopScreenSaver::class.java,
            ) {
                it.GetActive()
            },
            screenSaver("org.gnome.ScreenSaver", "/org/gnome/ScreenSaver", GnomeScreenSaver::class.java) {
                it.GetActive()
            },
            screenSaver("org.cinnamon.ScreenSaver", "/org/cinnamon/ScreenSaver", CinnamonScreenSaver::class.java) {
                it.GetActive()
            },
            screenSaver("org.mate.ScreenSaver", "/org/mate/ScreenSaver", MateScreenSaver::class.java) {
                it.GetActive()
            },
        )

        private fun <I : DBusInterface> screenSaver(
            iface: String,
            path: String,
            type: Class<I>,
            active: (I) -> Boolean,
        ) = ScreenSaver(iface) { connection -> active(connection.getRemoteObject(iface, path, type)) }
    }
}

/** A desktop screen saver on the session bus: its interface (also its bus name) and how to ask it on a connection. */
private class ScreenSaver(val iface: String, val isActive: (DBusConnection) -> Boolean)

@DBusInterfaceName("org.freedesktop.ScreenSaver")
private interface FreedesktopScreenSaver : DBusInterface {
    fun GetActive(): Boolean
}

@DBusInterfaceName("org.gnome.ScreenSaver")
private interface GnomeScreenSaver : DBusInterface {
    fun GetActive(): Boolean
}

@DBusInterfaceName("org.cinnamon.ScreenSaver")
private interface CinnamonScreenSaver : DBusInterface {
    fun GetActive(): Boolean
}

@DBusInterfaceName("org.mate.ScreenSaver")
private interface MateScreenSaver : DBusInterface {
    fun GetActive(): Boolean
}
