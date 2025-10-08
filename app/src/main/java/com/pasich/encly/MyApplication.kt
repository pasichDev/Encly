package com.pasich.encly

import android.app.Application
import android.util.Log
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
                Log.d("MyApplication", "Application initialized successfully")
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e("MyApplication", "Failed to initialize application", e)
            }

        }
    }

}
