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
 * encrypted database. Only reminder metadata (id/title/description/time) is persisted,
 * and this object is the single owner of that mirror.
 */
object TaskReminderScheduler {

    fun scheduleReminder(context: Context, task: Task) {
        val reminderDate = task.reminderDate ?: return
        scheduleReminderRaw(context, task.id, task.title, task.description, reminderDate)
    }

    /** Schedules a reminder from primitive fields (no Task / no DB needed) and mirrors it. */
    fun scheduleReminderRaw(
        context: Context,
        id: Long,
        title: String,
        description: String?,
        triggerAt: Long
    ) {
        if (triggerAt <= System.currentTimeMillis()) {
            // Time already passed — show it now (nothing to persist).
            NotificationHelper.showTaskNotification(context, id, title, description)
            return
        }
        setAlarm(context, id, title, description, triggerAt)
        ReminderStore.put(context, ReminderStore.ReminderRecord(id, title, description, triggerAt))
    }

    /** Sets (or replaces) the exact AlarmManager alarm; does not touch the mirror store. */
    private fun setAlarm(
        context: Context,
        id: Long,
        title: String,
        description: String?,
        triggerAt: Long
    ) {
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

    /**
     * Called when a reminder alarm fires: shows the notification and drops the mirror
     * record. Keeps the "fired ⇒ forget" invariant inside the scheduler (the receiver
     * never touches [ReminderStore] directly).
     */
    fun onAlarmFired(context: Context, id: Long, title: String, description: String?) {
        NotificationHelper.showTaskNotification(context, id, title, description)
        ReminderStore.remove(context, id)
    }

    /**
     * Reschedules future reminders from the plaintext store (e.g. after a reboot).
     * Only re-arms the alarms and drops expired records in a single store write —
     * it does not re-persist unchanged records.
     */
    fun rescheduleAllReminders(context: Context) {
        val now = System.currentTimeMillis()
        val records = ReminderStore.all(context)
        val future = records.filter { it.triggerAt > now }
        future.forEach { setAlarm(context, it.id, it.title, it.description, it.triggerAt) }
        if (future.size != records.size) {
            ReminderStore.replaceAll(context, future)
        }
    }
}
