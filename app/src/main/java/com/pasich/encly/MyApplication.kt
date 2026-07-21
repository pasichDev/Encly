package com.pasich.encly

import android.app.Application
import com.pasich.encly.core.AppLogger
import com.pasich.encly.utils.NotificationHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        try {
            // Ініціалізуємо notification channel
            NotificationHelper.createNotificationChannel(this)
            if (BuildConfig.DEBUG) {
                AppLogger.d("MyApplication", "Application initialized successfully")
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                AppLogger.e("MyApplication", "Failed to initialize application", e)
            }

        }
    }

}
