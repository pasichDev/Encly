package com.pasich.encly

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.pasich.encly.core.AppLogger
import com.pasich.encly.core.di.AppGraph
import com.pasich.encly.core.di.AppGraphOwner
import com.pasich.encly.core.di.ApplicationScope
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.data.handoff.HandoffStagingSweeper
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MyApplication :
    Application(),
    AppGraphOwner {

    override val appGraph: AppGraph by lazy {
        createGraphFactory<AppGraph.Factory>().create(this)
    }

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
        appGraph.inject(this)
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(sessionLockManager)
        } catch (e: Exception) {
            AppLogger.e("MyApplication", "Failed to initialize application", e)
        }
        // A My Notes hand-off (plaintext) that a killed process left in the cache; off the main thread.
        appScope.launch { handoffStaging.sweep() }
    }
}
