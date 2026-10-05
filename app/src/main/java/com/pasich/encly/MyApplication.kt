package com.pasich.encly

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.pasich.encly.core.AppLogger
import com.pasich.encly.core.di.ApplicationScope
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.data.handoff.HandoffStagingSweeper
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MyApplication : Application() {

    // Observes process foreground/background to auto-lock the app when it is backgrounded.
    @Inject
    lateinit var sessionLockManager: SessionLockManager

    @Inject
    lateinit var handoffStaging: HandoffStagingSweeper

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(sessionLockManager)
        } catch (e: Exception) {
            AppLogger.e("MyApplication", "Failed to initialize application", e)
        }
        // A My Notes hand-off (plaintext) that a killed process left in the cache; off the main thread.
        appScope.launch { handoffStaging.sweep() }
    }
}
