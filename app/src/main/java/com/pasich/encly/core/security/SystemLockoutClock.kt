package com.pasich.encly.core.security

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.pasich.encly.core.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** [LockoutClock] on `SystemClock.elapsedRealtime` and the system's boot counter. */
@SingleIn(AppScope::class)
@Inject
class SystemLockoutClock(@param:ApplicationContext private val context: Context) : LockoutClock {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

    override fun bootCount(): Int = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    } catch (_: RuntimeException) {
        -1
    }
}
