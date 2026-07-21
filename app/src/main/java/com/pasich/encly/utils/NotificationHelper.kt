package com.pasich.encly.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pasich.encly.MainActivity
import com.pasich.encly.R
import com.pasich.encly.data.model.Task
import com.pasich.encly.receiver.TaskNotificationReceiver

object NotificationHelper {
    
    const val CHANNEL_ID = "task_reminders"
    const val CHANNEL_NAME = "Нагадування завдань"
    const val CHANNEL_DESCRIPTION = "Сповіщення про завдання"
    
    const val ACTION_COMPLETE_TASK = "com.pasich.encly.COMPLETE_TASK"
    const val ACTION_SNOOZE_TASK = "com.pasich.encly.SNOOZE_TASK"
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_TASK_TITLE = "task_title"
    const val EXTRA_TASK_DESCRIPTION = "task_description"
    
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                setShowBadge(true)
            }
            
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun showTaskNotification(context: Context, task: Task) =
        showTaskNotification(context, task.id, task.title, task.description)

    /**
     * Shows a reminder notification from primitive fields, so it can be built without
     * reading the encrypted database (which may be locked when the alarm fires). The
     * snooze action carries the title/description so it can reschedule without the DB.
     */
    fun showTaskNotification(context: Context, taskId: Long, title: String, description: String?) {
        if (!hasNotificationPermission(context)) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, taskId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action buttons
        val completeIntent = Intent(context, TaskNotificationReceiver::class.java).apply {
            action = ACTION_COMPLETE_TASK
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context, (taskId * 2).toInt(), completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, TaskNotificationReceiver::class.java).apply {
            action = ACTION_SNOOZE_TASK
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, title)
            putExtra(EXTRA_TASK_DESCRIPTION, description)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context, (taskId * 2 + 1).toInt(), snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Нагадування про завдання")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(description ?: title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_launcher, "Виконано", completePendingIntent)
            .addAction(R.drawable.ic_launcher, "Через 10 хв", snoozePendingIntent)

        with(NotificationManagerCompat.from(context)) {
            notify(taskId.toInt(), builder.build())
        }
    }
    
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    fun cancelNotification(context: Context, taskId: Long) {
        with(NotificationManagerCompat.from(context)) {
            cancel(taskId.toInt())
        }
    }
}
