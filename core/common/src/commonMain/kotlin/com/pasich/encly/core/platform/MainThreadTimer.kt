package com.pasich.encly.core.platform

/** Runs an action on the main thread after a delay (the lock timers; the UI thread of the platform). */
public fun interface MainThreadTimer {
    /** Schedules [action] after [delayMs]; the returned function cancels it. */
    public fun schedule(delayMs: Long, action: () -> Unit): () -> Unit

    /** Never runs anything (unit tests that drive the callers by hand). */
    public object None : MainThreadTimer {
        override fun schedule(delayMs: Long, action: () -> Unit): () -> Unit = {}
    }
}
