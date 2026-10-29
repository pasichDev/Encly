package com.pasich.encly.desktop.platform

import com.pasich.encly.core.platform.MainThreadTimer
import com.pasich.encly.core.security.PinHardwareFactor
import com.pasich.encly.desktop.nativeos.NativeBootClock
import com.pasich.encly.desktop.nativeos.NativeCaptureExclusion
import com.pasich.encly.desktop.nativeos.NativeScreenLock
import com.pasich.encly.desktop.security.BootClockSource
import com.pasich.encly.desktop.security.OsLockSignals
import com.pasich.encly.desktop.security.SwingMainThreadTimer
import com.pasich.encly.desktop.storage.DesktopOs
import java.awt.Frame
import java.awt.Window
import java.awt.datatransfer.Clipboard
import java.nio.file.Path

/** Keeps a window out of screenshots and screen recordings where the OS lets an application do that. */
interface CaptureExclusion {
    /** False where the OS has no such feature, or the native call could not be set up. */
    val supported: Boolean

    /** Excludes [window]; false if it was not (not supported, or the OS refused). Safe to repeat. */
    fun exclude(window: Window): Boolean
}

/** Hands a web or mail address to the default application of the OS. */
fun interface LinkOpener {
    /** Opens [uri]; false if there is no application for it. */
    fun open(uri: java.net.URI): Boolean
}

/** The frame the app's own dialogs belong to, set by the window host once the window exists. */
class DialogParent {
    @Volatile
    var frame: Frame? = null
}

/**
 * Everything the desktop app needs from the machine that tests must replace: the places, the OS secret
 * store, and the calls into the OS. [system] is the real one; the graph and the services only see this.
 */
class DesktopEnvironment(
    val os: DesktopOs,
    val dataDirectory: Path,
    val pinFactor: PinHardwareFactor,
    val lockSignals: OsLockSignals,
    val bootClock: BootClockSource,
    val captureExclusion: CaptureExclusion,
    val fileDialogs: FileDialogs,
    val linkOpener: LinkOpener,
    val systemClipboard: () -> Clipboard?,
    val timer: MainThreadTimer = SwingMainThreadTimer(),
    val dialogParent: DialogParent = DialogParent(),
) {
    companion object {
        /**
         * The environment of this machine, around [dataDirectory] and the real [pinFactor]. The file dialogs
         * are the OS's own: the freedesktop portal on Linux when it answers, AWT's native dialogs otherwise.
         */
        fun system(
            os: DesktopOs,
            dataDirectory: Path,
            pinFactor: PinHardwareFactor,
            dialogParent: DialogParent = DialogParent(),
        ): DesktopEnvironment {
            val awtDialogs = AwtFileDialogs(os, dialogParent::frame)
            return DesktopEnvironment(
                os = os,
                dataDirectory = dataDirectory,
                pinFactor = pinFactor,
                lockSignals = NativeScreenLock.forOs(os),
                bootClock = NativeBootClock.forOs(os),
                captureExclusion = NativeCaptureExclusion.forOs(os),
                fileDialogs = if (os == DesktopOs.LINUX) {
                    com.pasich.encly.desktop.nativeos.PortalFileDialogs(awtDialogs)
                } else {
                    awtDialogs
                },
                linkOpener = AwtLinkOpener,
                systemClipboard = { runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard }.getOrNull() },
                dialogParent = dialogParent,
            )
        }
    }
}
