@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "TooGenericExceptionCaught")

package com.pasich.encly.desktop.nativeos

import com.pasich.encly.desktop.storage.BackupExclusion
import com.pasich.encly.desktop.storage.DesktopOs
import com.pasich.encly.desktop.storage.TimeMachineExclusion
import com.sun.jna.Library
import com.sun.jna.Native
import java.nio.file.Path

// JNA glue: it only runs against the real OS and is excluded from the coverage gate (the attribute value it writes,
// TimeMachineExclusion, is tested).

/** The backup exclusion of [os]; nothing to do outside macOS. */
object NativeBackupExclusion {
    fun forOs(os: DesktopOs): BackupExclusion =
        if (os == DesktopOs.MACOS) MacBackupExclusion() else BackupExclusion { true }
}

private interface MacLibC : Library {
    @Suppress("LongParameterList") // The signature of setxattr(2).
    fun setxattr(path: String, name: String, value: ByteArray, size: Long, position: Int, options: Int): Int
}

/** macOS: the sticky Time Machine exclusion, an extended attribute on the directory itself. */
private class MacBackupExclusion : BackupExclusion {
    private val libc: MacLibC? = try {
        Native.load("c", MacLibC::class.java)
    } catch (_: Throwable) {
        null
    }

    override fun exclude(directory: Path): Boolean {
        val lib = libc ?: return false
        val value = TimeMachineExclusion.attributeValue()
        val path = directory.toAbsolutePath().toString()
        return try {
            lib.setxattr(path, TimeMachineExclusion.ATTRIBUTE, value, value.size.toLong(), 0, 0) == 0
        } catch (_: Throwable) {
            false
        }
    }
}
