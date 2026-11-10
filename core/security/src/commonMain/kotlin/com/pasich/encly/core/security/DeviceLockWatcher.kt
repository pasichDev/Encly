package com.pasich.encly.core.security

/** Screen-off and keyguard signals for [SessionLockManager]; an interface so tests can drive it. */
public interface DeviceLockWatcher {
    /** Whether the keyguard is up and needs the user's credential. */
    public fun isDeviceLocked(): Boolean

    /** Calls [onScreenOff] when the screen turns off, until the returned function is called. */
    public fun watchScreenOff(onScreenOff: () -> Unit): () -> Unit

    /** No device signals (JVM tests). */
    public object None : DeviceLockWatcher {
        override fun isDeviceLocked(): Boolean = false
        override fun watchScreenOff(onScreenOff: () -> Unit): () -> Unit = {}
    }
}
