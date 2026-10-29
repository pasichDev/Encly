package com.pasich.encly.core.security

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.pasich.encly.core.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** [DeviceLockWatcher] on the KeyguardManager and the screen-off broadcast. */
@SingleIn(AppScope::class)
@Inject
class SystemDeviceLockWatcher(@param:ApplicationContext private val context: Context) : DeviceLockWatcher {
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
