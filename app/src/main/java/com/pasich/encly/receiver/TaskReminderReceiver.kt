package com.pasich.encly.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TaskReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("task_id", -1L)
        intent.getStringExtra("task_title") ?: return
        intent.getStringExtra("task_description")

        if (taskId == -1L) return
// TODO
        /*   val scope = CoroutineScope(Dispatchers.IO)
    scope.launch {
        val database = AppDatabase.Companion.getInstance(context)
        val task = database.tasksDao().getTaskById(taskId)

        // Перевіряємо, чи завдання ще не виконане
        if (task != null && !task.isCompleted) {
            NotificationHelper.showTaskNotification(context, task)
        }
    }

         */
    }
}