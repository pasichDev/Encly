package com.pasich.encly.core.platform

import android.os.Handler
import android.os.Looper
import dev.zacsweers.metro.Inject

/** [MainThreadTimer] on a Handler of the main Looper. */
@Inject
class HandlerMainThreadTimer : MainThreadTimer {
    override fun schedule(delayMs: Long, action: () -> Unit): () -> Unit {
        val handler = Handler(Looper.getMainLooper())
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMs)
        val cancel: () -> Unit = { handler.removeCallbacks(runnable) }
        return cancel
    }
}
