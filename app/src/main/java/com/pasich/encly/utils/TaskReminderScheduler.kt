package com.pasich.encly.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.pasich.encly.data.model.Task
import com.pasich.encly.receiver.TaskReminderReceiver

/**
 * Schedules exact task reminders via AlarmManager and mirrors them into [ReminderStore]
 * (plaintext) so they can be rescheduled after a reboot without touching the locked,
 * encrypted database. Only reminder metadata (id/title/description/time) is persisted.
 */
object TaskReminderScheduler {

    fun scheduleReminder(context: Context, task: Task) {
        val reminderDate = task.reminderDate ?: return
        scheduleReminderRaw(context, task.id, task.title, task.description, reminderDate)
    }

    /** Schedules a reminder from primitive fields (no Task / no DB needed). */
    fun scheduleReminderRaw(
        context: Context,
        id: Long,
        title: String,
        description: String?,
        triggerAt: Long
    ) {
        if (triggerAt <= System.currentTimeMillis()) {
            // Time already passed — show it now.
            NotificationHelper.showTaskNotification(context, id, title, description)
            return
        }

        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            putExtra("task_id", id)
            putExtra("task_title", title)
            putExtra("task_description", description)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } catch (_: SecurityException) {
            // No exact-alarm permission — fall back to an inexact alarm.
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }

        // Persist for reboot rescheduling (plaintext metadata only).
        ReminderStore.put(context, ReminderStore.ReminderRecord(id, title, description, triggerAt))
    }

    fun cancelReminder(context: Context, taskId: Long) {
        val intent = Intent(context, TaskReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
        ReminderStore.remove(context, taskId)
    }

    /** Reschedules future reminders from the plaintext store (e.g. after a reboot). */
    fun rescheduleAllReminders(context: Context) {
        val now = System.currentTimeMillis()
        ReminderStore.all(context).forEach { record ->
            if (record.triggerAt > now) {
                scheduleReminderRaw(context, record.id, record.title, record.description, record.triggerAt)
            } else {
                ReminderStore.remove(context, record.id)
            }
        }
    }
}
