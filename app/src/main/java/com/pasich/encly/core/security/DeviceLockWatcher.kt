package com.pasich.encly.core.security

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Screen-off and keyguard signals for [SessionLockManager]; an interface so tests can drive it. */
interface DeviceLockWatcher {
    /** Whether the keyguard is up and needs the user's credential. */
    fun isDeviceLocked(): Boolean

    /** Calls [onScreenOff] when the screen turns off, until the returned function is called. */
    fun watchScreenOff(onScreenOff: () -> Unit): () -> Unit

    /** No device signals (JVM tests). */
    object None : DeviceLockWatcher {
        override fun isDeviceLocked(): Boolean = false
        override fun watchScreenOff(onScreenOff: () -> Unit): () -> Unit = {}
    }
}

@Singleton
class SystemDeviceLockWatcher @Inject constructor(@param:ApplicationContext private val context: Context) :
    DeviceLockWatcher {
    override fun isDeviceLocked(): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true

    override fun watchScreenOff(onScreenOff: () -> Unit): () -> Unit {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) onScreenOff()
            }
        }
        return try {
            // A protected system broadcast: NOT_EXPORTED still receives it.
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            val stop: () -> Unit = {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: IllegalArgumentException) {
                    // Already unregistered.
                }
            }
            stop
        } catch (_: RuntimeException) {
            {}
        }
    }
}
