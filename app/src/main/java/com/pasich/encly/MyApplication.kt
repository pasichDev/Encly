package com.pasich.encly

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.pasich.encly.core.AppLogger
import com.pasich.encly.core.security.SessionLockManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MyApplication : Application() {

    // Observes process foreground/background to auto-lock the app when it is backgrounded.
    @Inject
    lateinit var sessionLockManager: SessionLockManager

    override fun onCreate() {
        super.onCreate()
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(sessionLockManager)
        } catch (e: Exception) {
            AppLogger.e("MyApplication", "Failed to initialize application", e)
        }
    }
}
