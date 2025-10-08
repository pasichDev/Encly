package com.pasich.encly.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pasich.encly.service.TaskReminderService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Перепланувати всі нагадування після перезавантаження
            val serviceIntent = Intent(context, TaskReminderService::class.java).apply {
                action = TaskReminderService.Companion.ACTION_RESCHEDULE_REMINDERS
            }
            context.startService(serviceIntent)
        }
    }
}