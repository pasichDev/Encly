package com.pasich.encly.platform

import java.awt.Toolkit
import java.awt.event.KeyEvent

/** The AWT lock-key state; some systems (a Linux session without it, a headless one) refuse to say. */
internal actual fun capsLockOn(): Boolean? =
    capsLockFrom { Toolkit.getDefaultToolkit().getLockingKeyState(KeyEvent.VK_CAPS_LOCK) }

/** What [read] says, or null when the system refuses (a headless one throws a subclass of the same exception). */
internal fun capsLockFrom(read: () -> Boolean): Boolean? = try {
    read()
} catch (_: UnsupportedOperationException) {
    null
}
