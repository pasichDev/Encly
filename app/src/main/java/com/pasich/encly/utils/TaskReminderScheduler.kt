package com.pasich.encly.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pasich.encly.data.model.Task
import com.pasich.encly.receiver.TaskReminderReceiver

object TaskReminderScheduler {
    
    fun scheduleReminder(context: Context, task: Task) {
        val reminderDate = task.reminderDate ?: return
        
        if (reminderDate <= System.currentTimeMillis()) {
            // Якщо час вже минув, показуємо нотифікацію зараз
            NotificationHelper.showTaskNotification(context, task)
            return
        }
        
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            putExtra("task_id", task.id)
            putExtra("task_title", task.title)
            putExtra("task_description", task.description)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderDate,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    reminderDate,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Fallback для випадків, коли немає дозволу на точні будильники
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                reminderDate,
                pendingIntent
            )
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
    }
    
    suspend fun rescheduleAllReminders(context: Context) {
        // TODO
     /*   val database = AppDatabase.getInstance(context)
        val tasks = database.tasksDao().getTasksWithReminder(System.currentTimeMillis())
        
        tasks.forEach { task ->
            if (!task.isCompleted) {
                scheduleReminder(context, task)
            }
        }

      */
    }
}
