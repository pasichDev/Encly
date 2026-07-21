package com.pasich.encly.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pasich.encly.utils.TaskReminderScheduler

/**
 * Fires when a task reminder alarm goes off. Delegates to the scheduler, which builds
 * the notification straight from the alarm extras (title/description) — so it works even
 * while the encrypted database is locked (no DB access).
 */
class TaskReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("task_id", -1L)
        val title = intent.getStringExtra("task_title") ?: return
        val description = intent.getStringExtra("task_description")
        if (taskId == -1L) return

        TaskReminderScheduler.onAlarmFired(context, taskId, title, description)
    }
}
