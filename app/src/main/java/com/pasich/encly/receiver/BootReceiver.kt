package com.pasich.encly.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pasich.encly.utils.TaskReminderScheduler

/**
 * Reschedules task reminder alarms after a device reboot (exact alarms don't survive a
 * reboot). Uses goAsync() so the short rescheduling work runs off the main thread and
 * without relying on a background-started Service.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                TaskReminderScheduler.rescheduleAllReminders(appContext)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
