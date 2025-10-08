package com.pasich.encly.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.pasich.encly.utils.TaskReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TaskReminderService : Service() {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CHECK_REMINDERS -> {
                checkAndShowReminders()
            }
            ACTION_RESCHEDULE_REMINDERS -> {
                rescheduleAllReminders()
            }
        }
        return START_NOT_STICKY
    }
    
    private fun checkAndShowReminders() {
        // TODO
        /*
        serviceScope.launch {
            try {
                val database = AppDatabase.getInstance(this@TaskReminderService)
                val currentTime = System.currentTimeMillis()
                val tasksWithReminders = database.tasksDao().getTasksWithReminder(currentTime)
                
                tasksWithReminders.forEach { task ->
                    if (!task.isCompleted && task.reminderDate != null && task.reminderDate <= currentTime) {
                        NotificationHelper.showTaskNotification(this@TaskReminderService, task)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stopSelf()
            }
        }

         */
    }
    
    private fun rescheduleAllReminders() {
        serviceScope.launch {
            try {
                TaskReminderScheduler.rescheduleAllReminders(this@TaskReminderService)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stopSelf()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
    
    companion object {
        const val ACTION_CHECK_REMINDERS = "com.pasich.encly.CHECK_REMINDERS"
        const val ACTION_RESCHEDULE_REMINDERS = "com.pasich.encly.RESCHEDULE_REMINDERS"
    }
}
