@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "TooGenericExceptionCaught", "ReturnCount")

package com.pasich.encly.desktop.nativeos

import com.pasich.encly.desktop.platform.CaptureExclusion
import com.pasich.encly.desktop.storage.DesktopOs
import com.sun.jna.Callback
import com.sun.jna.FunctionMapper
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window
import java.lang.reflect.Method
import java.util.Collections

// Keeps the window out of screenshots and screen recordings, per OS. JNA glue: it only runs against a real
// window and is excluded from the coverage gate.

/** The capture exclusion of [os]; Linux has none (see [UnavailableCaptureExclusion]). */
object NativeCaptureExclusion {
    fun forOs(os: DesktopOs): CaptureExclusion = when (os) {
        DesktopOs.WINDOWS -> WindowsCaptureExclusion()
        DesktopOs.MACOS -> MacCaptureExclusion()
        DesktopOs.LINUX -> UnavailableCaptureExclusion
    }
}

/**
 * X11 and Wayland have no way for an application to keep a window out of the compositor's screenshots and
 * recordings, so nothing is done on Linux. The vault is still locked on idle, focus loss and screen lock.
 */
object UnavailableCaptureExclusion : CaptureExclusion {
    override val supported: Boolean = false

    override fun exclude(window: Window): Boolean = false
}

private interface WindowsUser32Display : StdCallLibrary {
    fun SetWindowDisplayAffinity(hwnd: WinDef.HWND, affinity: Int): Boolean
}

/**
 * Windows 10 version 2004 and later: `WDA_EXCLUDEFROMCAPTURE` removes the window from every capture. Earlier
 * versions only know `WDA_MONITOR`, which shows the window as a black rectangle in a capture; that is the
 * fallback.
 */
private class WindowsCaptureExclusion : CaptureExclusion {
    private val user32: WindowsUser32Display? = try {
        Native.load("user32", WindowsUser32Display::class.java)
    } catch (_: Throwable) {
        null
    }

    override val supported: Boolean get() = user32 != null

    override fun exclude(window: Window): Boolean {
        val lib = user32 ?: return false
        return try {
            val hwnd = WinDef.HWND(Native.getComponentPointer(window))
            lib.SetWindowDisplayAffinity(hwnd, WDA_EXCLUDEFROMCAPTURE) ||
                lib.SetWindowDisplayAffinity(hwnd, WDA_MONITOR)
        } catch (_: Throwable) {
            false
        }
    }

    private companion object {
        const val WDA_MONITOR = 0x01
        const val WDA_EXCLUDEFROMCAPTURE = 0x11
    }
}

private interface ObjectiveC : Library {
    fun objc_getClass(name: String): Pointer?

    fun sel_registerName(name: String): Pointer

    // objc_msgSend is not variadic in practice (and a variadic call is wrong on arm64): one declaration per
    // signature, all mapped to the same native symbol by [MSG_SEND_MAPPER].
    fun objc_msgSend_ptr(receiver: Pointer, selector: Pointer): Pointer?

    fun objc_msgSend_long(receiver: Pointer, selector: Pointer): Long

    fun objc_msgSend_ptrIndex(receiver: Pointer, selector: Pointer, index: Long): Pointer?

    fun objc_msgSend_voidLong(receiver: Pointer, selector: Pointer, value: Long)
}

private interface Dispatch : Library {
    fun dispatch_async_f(queue: Pointer, context: Pointer?, work: DispatchWork)
}

private fun interface DispatchWork : Callback {
    fun invoke(context: Pointer?)
}

private val MSG_SEND_MAPPER = FunctionMapper { _: NativeLibrary, method: Method ->
    if (method.name.startsWith("objc_msgSend")) "objc_msgSend" else method.name
}

/**
 * macOS: `NSWindow.sharingType = NSWindowSharingNone` for every window of the app, so the window is left out
 * of `screencapture`, screen sharing and recordings by the window server. It is reached through the
 * Objective-C runtime (no access to AWT's private peer classes is needed) and applied on the AppKit main
 * thread. Best effort: Apple's newer ScreenCaptureKit based tools can ignore the flag on macOS 15 and later,
 * which is a limit of the OS and not of the call.
 */
private class MacCaptureExclusion : CaptureExclusion {
    private val objc: ObjectiveC? = try {
        Native.load("objc", ObjectiveC::class.java, mapOf(Library.OPTION_FUNCTION_MAPPER to MSG_SEND_MAPPER))
    } catch (_: Throwable) {
        null
    }
    private val dispatch: Dispatch? = try {
        Native.load("System", Dispatch::class.java)
    } catch (_: Throwable) {
        null
    }
    private val mainQueue: Pointer? = try {
        NativeLibrary.getInstance("System").getGlobalVariableAddress("_dispatch_main_q")
    } catch (_: Throwable) {
        null
    }

    // The native side calls these after dispatch_async_f returns: they must not be garbage collected before.
    private val pending = Collections.synchronizedSet(HashSet<DispatchWork>())

    override val supported: Boolean get() = objc != null && dispatch != null && mainQueue != null

    override fun exclude(window: Window): Boolean {
        val runtime = objc ?: return false
        val lib = dispatch ?: return false
        val queue = mainQueue ?: return false
        lateinit var work: DispatchWork
        work = DispatchWork {
            try {
                hideAllWindows(runtime)
            } finally {
                pending -= work
            }
        }
        pending += work
        return try {
            lib.dispatch_async_f(queue, null, work)
            true
        } catch (_: Throwable) {
            pending -= work
            false
        }
    }

    private fun hideAllWindows(runtime: ObjectiveC) {
        try {
            val application = runtime.objc_getClass("NSApplication") ?: return
            val shared = runtime.objc_msgSend_ptr(application, runtime.sel_registerName("sharedApplication")) ?: return
            val windows = runtime.objc_msgSend_ptr(shared, runtime.sel_registerName("windows")) ?: return
            val count = runtime.objc_msgSend_long(windows, runtime.sel_registerName("count"))
            val objectAt = runtime.sel_registerName("objectAtIndex:")
            val setSharing = runtime.sel_registerName("setSharingType:")
            for (index in 0 until count) {
                val nsWindow = runtime.objc_msgSend_ptrIndex(windows, objectAt, index) ?: continue
                runtime.objc_msgSend_voidLong(nsWindow, setSharing, NS_WINDOW_SHARING_NONE)
            }
        } catch (_: Throwable) {
            // The window stays capturable; nothing else can be done from here.
        }
    }

    private companion object {
        const val NS_WINDOW_SHARING_NONE = 0L
    }
}
