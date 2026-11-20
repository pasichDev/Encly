package com.pasich.encly.core.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.pasich.encly.core.di.ApplicationContext
import com.pasich.encly.core.security.LOCK_FLUSH_TIMEOUT_MS
import dev.zacsweers.metro.Inject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * [MainThreadTimer] for the lock timers: a [HandlerMainThreadTimer] plus an `AlarmManager` alarm for the same delay.
 *
 * Android 14+ freezes a cached app about ten seconds after it left the screen, and a frozen process runs no
 * `Handler` callbacks and receives no context-registered broadcasts, so the auto-lock delay would not fire and the
 * vault key would stay in memory until the user came back. The alarm is delivered to [AwayLockReceiver], which the
 * system unfreezes the process for. The alarm is inexact (`setAndAllowWhileIdle`, so no exact-alarm permission is
 * needed): the lock may come a little after the delay, never before it. The `Handler` still serves the case where the
 * process is not frozen yet, and whichever fires second finds nothing left to do (the action is idempotent), and
 * cancelling cancels both. An alarm that outlives its process finds no action and does nothing.
 */
@Inject
class AlarmMainThreadTimer(
    @param:ApplicationContext private val context: Context,
    private val handlerTimer: HandlerMainThreadTimer,
) : MainThreadTimer {
    override fun schedule(delayMs: Long, action: () -> Unit): () -> Unit {
        val id = nextId.incrementAndGet()
        actions[id] = action
        val cancelAlarm = setAlarm(id, delayMs)
        val cancelHandler = handlerTimer.schedule(delayMs) {
            // The Handler ran first: the alarm is no longer needed.
            cancelAlarm()
            actions.remove(id)
            action()
        }
        return {
            cancelHandler()
            cancelAlarm()
            actions.remove(id)
        }
    }

    /** Returns the function that cancels the alarm; a no-op when none could be set. */
    private fun setAlarm(id: Int, delayMs: Long): () -> Unit {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return {}
        val pending = PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, AwayLockReceiver::class.java)
                .setAction(AwayLockReceiver.ACTION)
                .putExtra(AwayLockReceiver.EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return try {
            alarms.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delayMs,
                pending,
            )
            val cancel: () -> Unit = {
                alarms.cancel(pending)
                pending.cancel()
            }
            cancel
        } catch (_: SecurityException) {
            {}
        }
    }

    internal companion object {
        private val nextId = AtomicInteger(0)

        /** The actions waiting for their alarm, by alarm id; this process only. */
        private val actions = ConcurrentHashMap<Int, () -> Unit>()

        /** Runs and removes the action of [id] on the calling (main) thread; false when there is none. */
        fun fire(id: Int): Boolean {
            val action = actions.remove(id) ?: return false
            action()
            return true
        }
    }
}

/**
 * Receives the away-lock alarm of [AlarmMainThreadTimer]. Not exported: only the app's own `PendingIntent` reaches
 * it. The action runs on the main thread, where a lock may wait up to [LOCK_FLUSH_TIMEOUT_MS] for the editors' saves,
 * so the broadcast is kept open a little longer than that and the process is not frozen again before the vault is
 * closed.
 */
class AwayLockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val result = goAsync()
        if (AlarmMainThreadTimer.fire(intent.getIntExtra(EXTRA_ID, -1))) {
            Handler(Looper.getMainLooper()).postDelayed({ result.finish() }, LOCK_FLUSH_TIMEOUT_MS + HOLD_MARGIN_MS)
        } else {
            result.finish()
        }
    }

    internal companion object {
        const val ACTION = "com.pasich.encly.action.AWAY_LOCK"
        const val EXTRA_ID = "id"
        private const val HOLD_MARGIN_MS = 500L
    }
}
