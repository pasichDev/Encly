package com.pasich.encly.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pasich.encly.utils.NotificationHelper

class TaskNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(NotificationHelper.EXTRA_TASK_ID, -1L)
        if (taskId == -1L) return

        // TODO тут все ок але якщо зашифрована бд потрібно передбачити всі сценарії

    /*    val database = AppDatabase.Companion.getInstance(context)
        val scope = CoroutineScope(Dispatchers.IO)

        when (intent.action) {
            NotificationHelper.ACTION_COMPLETE_TASK -> {
                scope.launch {
                    database.tasksDao().updateTaskStatus(
                        taskId,
                        true,
                        System.currentTimeMillis()
                    )
                    NotificationHelper.cancelNotification(context, taskId)
                }
            }
            NotificationHelper.ACTION_SNOOZE_TASK -> {
                scope.launch {
                    val task = database.tasksDao().getTaskById(taskId)
                    task?.let {
                        val calendar = Calendar.getInstance()
                        calendar.add(Calendar.MINUTE, 10)
                        val newReminderTime = calendar.timeInMillis

                        val updatedTask = it.copy(reminderDate = newReminderTime)
                        database.tasksDao().updateTask(updatedTask)

                        // Заплануємо нове нагадування
                        TaskReminderScheduler.scheduleReminder(context, updatedTask)
                        NotificationHelper.cancelNotification(context, taskId)
                    }
                }
            }
        } */
    }


}