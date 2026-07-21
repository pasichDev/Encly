package com.pasich.encly.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pasich.encly.utils.NotificationHelper
import com.pasich.encly.utils.ReminderStore
import com.pasich.encly.utils.TaskReminderScheduler

/**
 * Handles the notification action buttons. Because the encrypted database may be
 * locked here, neither action touches it:
 * - Complete: queue the completion (applied on next unlock) and dismiss.
 * - Snooze: reschedule +10 min from the intent extras (no DB needed).
 */
class TaskNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(NotificationHelper.EXTRA_TASK_ID, -1L)
        if (taskId == -1L) return

        when (intent.action) {
            NotificationHelper.ACTION_COMPLETE_TASK -> {
                ReminderStore.enqueueComplete(context, taskId)
                TaskReminderScheduler.cancelReminder(context, taskId)
                NotificationHelper.cancelNotification(context, taskId)
            }

            NotificationHelper.ACTION_SNOOZE_TASK -> {
                val title = intent.getStringExtra(NotificationHelper.EXTRA_TASK_TITLE)
                    ?: "Нагадування"
                val description = intent.getStringExtra(NotificationHelper.EXTRA_TASK_DESCRIPTION)
                val newTime = System.currentTimeMillis() + 10 * 60 * 1000
                TaskReminderScheduler.scheduleReminderRaw(context, taskId, title, description, newTime)
                NotificationHelper.cancelNotification(context, taskId)
            }
        }
    }
}
