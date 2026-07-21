package com.pasich.encly.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pasich.encly.utils.NotificationHelper
import com.pasich.encly.utils.ReminderStore

/**
 * Fires when a task reminder alarm goes off. Builds the notification straight from
 * the alarm extras (title/description) so it works even while the encrypted database
 * is locked — no DB access.
 */
class TaskReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("task_id", -1L)
        val title = intent.getStringExtra("task_title") ?: return
        val description = intent.getStringExtra("task_description")
        if (taskId == -1L) return

        NotificationHelper.showTaskNotification(context, taskId, title, description)

        // The alarm has fired; drop the stored record (snooze re-adds it if used).
        ReminderStore.remove(context, taskId)
    }
}
