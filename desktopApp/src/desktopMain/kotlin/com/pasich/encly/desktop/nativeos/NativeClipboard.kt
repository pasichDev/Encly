@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "TooGenericExceptionCaught", "ReturnCount")

package com.pasich.encly.desktop.nativeos

import com.pasich.encly.desktop.platform.ClipboardWriter
import com.pasich.encly.desktop.storage.DesktopOs
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.BaseTSD
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

// Text on the system clipboard as a clip that clipboard history, cloud sync and Universal Clipboard must not keep.
// AWT has no way to say that, so the clip is written with the OS's own calls. JNA glue: it only runs against the real
// OS and is excluded from the coverage gate (the code that chooses between it and AWT is tested with a fake).

/** The sensitive clipboard writer of [os], or null where the OS has no such markers (Linux) or can't be set up. */
object NativeClipboard {
    fun forOs(os: DesktopOs): ClipboardWriter? = when (os) {
        DesktopOs.MACOS -> MacPasteboard.create()
        DesktopOs.WINDOWS -> WindowsClipboard.create()
        DesktopOs.LINUX -> null
    }
}

/**
 * macOS: `NSPasteboard` with the types of nspasteboard.org next to the text: `org.nspasteboard.ConcealedType` (a
 * password-like clip, which clipboard managers skip) and `org.nspasteboard.TransientType` (not to be kept). The
 * Objective-C runtime is called through JNA with the fixed-argument convention of `objc_msgSend`.
 */
internal class MacPasteboard private constructor(private val objc: NativeLibrary) : ClipboardWriter {
    private val msgSend: Function = objc.getFunction("objc_msgSend")
    private val getClass: Function = objc.getFunction("objc_getClass")
    private val registerSelector: Function = objc.getFunction("sel_registerName")

    override fun write(text: String): Boolean = try {
        val pasteboard = send(classNamed("NSPasteboard"), "generalPasteboard") ?: return false
        send(pasteboard, "clearContents")
        val marker = nsString("")
        try {
            val value = nsString(text)
            try {
                put(pasteboard, value, TEXT_TYPE)
                put(pasteboard, marker, CONCEALED_TYPE)
                put(pasteboard, marker, TRANSIENT_TYPE)
            } finally {
                release(value)
            }
        } finally {
            release(marker)
        }
        true
    } catch (_: Throwable) {
        false
    }

    /** The types on the general pasteboard now, for the real-OS test. */
    fun types(): List<String> {
        val pasteboard = send(classNamed("NSPasteboard"), "generalPasteboard") ?: return emptyList()
        val types = send(pasteboard, "types") ?: return emptyList()
        val count = Pointer.nativeValue(send(types, "count"))
        return (0 until count).mapNotNull { index ->
            val type = send(types, "objectAtIndex:", index) ?: return@mapNotNull null
            send(type, "UTF8String")?.getString(0)
        }
    }

    private fun put(pasteboard: Pointer, value: Pointer, type: String) {
        val name = nsString(type)
        try {
            send(pasteboard, "setString:forType:", value, name)
        } finally {
            release(name)
        }
    }

    /** An owned (+1) `NSString`, which the caller releases: no autorelease pool is needed on a thread without one. */
    private fun nsString(value: String): Pointer {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val memory = Memory(bytes.size + 1L)
        memory.write(0, bytes, 0, bytes.size)
        memory.setByte(bytes.size.toLong(), 0)
        val allocated = send(classNamed("NSString"), "alloc") ?: error("NSString alloc failed")
        return send(allocated, "initWithUTF8String:", memory) ?: error("NSString init failed")
    }

    private fun release(value: Pointer) {
        send(value, "release")
    }

    private fun classNamed(name: String): Pointer = getClass.invokePointer(arrayOf<Any>(name))

    private fun send(receiver: Pointer, selector: String, vararg args: Any): Pointer? {
        val call = arrayOfNulls<Any>(args.size + 2)
        call[0] = receiver
        call[1] = registerSelector.invokePointer(arrayOf<Any>(selector))
        args.forEachIndexed { index, arg -> call[index + 2] = if (arg is Long) com.sun.jna.NativeLong(arg) else arg }
        return msgSend.invokePointer(call)
    }

    companion object {
        const val TEXT_TYPE = "public.utf8-plain-text"
        const val CONCEALED_TYPE = "org.nspasteboard.ConcealedType"
        const val TRANSIENT_TYPE = "org.nspasteboard.TransientType"

        fun create(): MacPasteboard? = try {
            MacPasteboard(NativeLibrary.getInstance("objc"))
        } catch (_: Throwable) {
            null
        }
    }
}

private interface WindowsClipboardApi : StdCallLibrary {
    fun OpenClipboard(owner: WinDef.HWND?): Boolean

    fun EmptyClipboard(): Boolean

    fun SetClipboardData(format: Int, handle: Pointer?): Pointer?

    fun CloseClipboard(): Boolean

    fun RegisterClipboardFormatW(name: WString): Int

    fun IsClipboardFormatAvailable(format: Int): Boolean
}

private interface WindowsMemoryApi : StdCallLibrary {
    fun GlobalAlloc(flags: Int, bytes: BaseTSD.SIZE_T): Pointer?

    fun GlobalLock(memory: Pointer): Pointer?

    fun GlobalUnlock(memory: Pointer): Boolean

    fun GlobalFree(memory: Pointer): Pointer?
}

/**
 * Windows: the text as `CF_UNICODETEXT` plus the three registered formats that tell the system to leave the clip
 * alone: `ExcludeClipboardContentFromMonitorProcessing` (no clipboard monitors), `CanIncludeInClipboardHistory` = 0
 * (no Win+V history) and `CanUploadToCloudClipboard` = 0 (no sync to the Microsoft account). The clipboard needs an
 * owner window to be set at all, so a message-only window of ours owns it.
 */
internal class WindowsClipboard private constructor(
    private val clipboard: WindowsClipboardApi,
    private val memory: WindowsMemoryApi,
    private val owner: WinDef.HWND,
) : ClipboardWriter {
    private val markerFormats: List<Int> = MARKER_NAMES.map { clipboard.RegisterClipboardFormatW(WString(it)) }

    override fun write(text: String): Boolean = try {
        if (!open()) {
            false
        } else {
            try {
                clipboard.EmptyClipboard()
                val textSet = set(CF_UNICODETEXT, (text + "\u0000").toByteArray(Charsets.UTF_16LE))
                // The markers are DWORDs of 0; their presence is what the system looks for.
                val markersSet = markerFormats.all { it != 0 && set(it, ByteArray(DWORD_SIZE)) }
                textSet && markersSet
            } finally {
                clipboard.CloseClipboard()
            }
        }
    } catch (_: Throwable) {
        false
    }

    /** Whether the clipboard now carries the marker named [name] (the real-OS test asks). */
    fun hasMarker(name: String): Boolean = clipboard.IsClipboardFormatAvailable(
        markerFormats[MARKER_NAMES.indexOf(name)],
    )

    /** Another program may hold the clipboard for a moment: a few tries before giving up. */
    private fun open(): Boolean {
        repeat(OPEN_TRIES) {
            if (clipboard.OpenClipboard(owner)) return true
            Thread.sleep(OPEN_PAUSE_MILLIS)
        }
        return false
    }

    /** Hands [bytes] to the clipboard in global memory it then owns; true when it took them. */
    private fun set(format: Int, bytes: ByteArray): Boolean {
        val handle = memory.GlobalAlloc(GMEM_MOVEABLE, BaseTSD.SIZE_T(bytes.size.toLong())) ?: return false
        val target = memory.GlobalLock(handle)
        if (target == null) {
            memory.GlobalFree(handle)
            return false
        }
        target.write(0, bytes, 0, bytes.size)
        memory.GlobalUnlock(handle)
        if (clipboard.SetClipboardData(format, handle) == null) {
            memory.GlobalFree(handle)
            return false
        }
        return true
    }

    companion object {
        const val EXCLUDE_FROM_MONITORS = "ExcludeClipboardContentFromMonitorProcessing"
        const val CAN_INCLUDE_IN_HISTORY = "CanIncludeInClipboardHistory"
        const val CAN_UPLOAD_TO_CLOUD = "CanUploadToCloudClipboard"

        private val MARKER_NAMES = listOf(EXCLUDE_FROM_MONITORS, CAN_INCLUDE_IN_HISTORY, CAN_UPLOAD_TO_CLOUD)
        private const val CF_UNICODETEXT = 13
        private const val GMEM_MOVEABLE = 0x0002
        private const val DWORD_SIZE = 4
        private const val OPEN_TRIES = 5
        private const val OPEN_PAUSE_MILLIS = 20L
        private const val OWNER_WINDOW_WAIT_SECONDS = 2L

        fun create(): WindowsClipboard? = try {
            val clipboard = Native.load("user32", WindowsClipboardApi::class.java)
            val memory = Native.load("kernel32", WindowsMemoryApi::class.java)
            startOwnerWindow()?.let { WindowsClipboard(clipboard, memory, it) }
        } catch (_: Throwable) {
            null
        }

        /**
         * The window that owns what we put on the clipboard: a message-only window ("STATIC" is a system class) with
         * a thread of its own that pumps its messages. Whoever empties the clipboard next sends it
         * `WM_DESTROYCLIPBOARD` and waits for the answer, so a window nobody pumps would hang that program (Encly's
         * own wipe included).
         */
        private fun startOwnerWindow(): WinDef.HWND? {
            val ready = CountDownLatch(1)
            val window = AtomicReference<WinDef.HWND?>()
            Thread({
                val user32 = User32.INSTANCE
                val hwnd = user32.CreateWindowEx(
                    0,
                    "STATIC",
                    "EnclyClipboard",
                    0,
                    0,
                    0,
                    0,
                    0,
                    WinUser.HWND_MESSAGE,
                    null,
                    null,
                    null,
                )
                window.set(hwnd)
                ready.countDown()
                if (hwnd != null) {
                    val message = WinUser.MSG()
                    while (user32.GetMessage(message, null, 0, 0) > 0) {
                        user32.TranslateMessage(message)
                        user32.DispatchMessage(message)
                    }
                }
            }, "encly-clipboard-owner").apply { isDaemon = true }.start()
            return if (ready.await(OWNER_WINDOW_WAIT_SECONDS, TimeUnit.SECONDS)) window.get() else null
        }
    }
}
