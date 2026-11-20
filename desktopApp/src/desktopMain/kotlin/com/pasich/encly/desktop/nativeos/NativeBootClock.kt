@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "TooGenericExceptionCaught", "ReturnCount")

package com.pasich.encly.desktop.nativeos

import com.pasich.encly.desktop.security.BootClockSource
import com.pasich.encly.desktop.security.parseProcUptime
import com.pasich.encly.desktop.storage.DesktopOs
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.LongByReference
import com.sun.jna.win32.StdCallLibrary
import java.nio.file.Files
import java.nio.file.Paths

// The boot clock of each OS. JNA glue: it only runs against the real OS and is excluded from the coverage gate
// (the pure parts, parseProcUptime and OsUptimeClock, are tested).

/** Picks the native uptime reader of [os]. Never throws: a reader that cannot start answers null. */
object NativeBootClock {
    fun forOs(os: DesktopOs): BootClockSource = when (os) {
        DesktopOs.LINUX -> LinuxBootClock
        DesktopOs.MACOS -> MacBootClock()
        DesktopOs.WINDOWS -> WindowsBootClock()
    }
}

/** /proc/uptime counts suspend (it is CLOCK_BOOTTIME); /proc/sys/kernel/random/boot_id is new on every boot. */
private object LinuxBootClock : BootClockSource {
    override fun uptimeMillis(): Long? = try {
        parseProcUptime(Files.readString(Paths.get("/proc/uptime")))
    } catch (_: Exception) {
        null
    }

    override fun bootId(): String? = try {
        Files.readString(Paths.get("/proc/sys/kernel/random/boot_id")).trim().takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
}

private interface MacSystem : Library {
    fun mach_continuous_time(): Long

    fun mach_timebase_info(info: IntArray): Int

    fun sysctlbyname(name: String, oldp: Pointer?, oldlenp: LongByReference, newp: Pointer?, newlen: Long): Int
}

/** mach_continuous_time keeps counting through sleep; kern.bootsessionuuid is new on every boot. */
private class MacBootClock : BootClockSource {
    private val system: MacSystem? = try {
        Native.load("System", MacSystem::class.java)
    } catch (_: Throwable) {
        null
    }
    private val timebase: IntArray? = system?.let {
        IntArray(2).takeIf { info -> it.mach_timebase_info(info) == 0 && info[1] != 0 }
    }

    override fun uptimeMillis(): Long? {
        val lib = system ?: return null
        val base = timebase ?: return null
        return try {
            lib.mach_continuous_time() * base[0] / base[1] / NANOS_PER_MILLI
        } catch (_: Throwable) {
            null
        }
    }

    override fun bootId(): String? {
        val lib = system ?: return null
        return try {
            val size = LongByReference(0)
            if (lib.sysctlbyname(BOOT_SESSION_UUID, null, size, null, 0) != 0 || size.value <= 0) return null
            val buffer = com.sun.jna.Memory(size.value)
            if (lib.sysctlbyname(BOOT_SESSION_UUID, buffer, size, null, 0) != 0) return null
            buffer.getString(0)
        } catch (_: Throwable) {
            null
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val BOOT_SESSION_UUID = "kern.bootsessionuuid"
    }
}

private interface WindowsKernel : StdCallLibrary {
    fun GetTickCount64(): Long
}

/** GetTickCount64 counts since boot, sleep included. Windows gives no boot id; the uptime comparison covers it. */
private class WindowsBootClock : BootClockSource {
    private val kernel: WindowsKernel? = try {
        Native.load("kernel32", WindowsKernel::class.java)
    } catch (_: Throwable) {
        null
    }

    override fun uptimeMillis(): Long? = try {
        kernel?.GetTickCount64()
    } catch (_: Throwable) {
        null
    }

    override fun bootId(): String? = null
}
