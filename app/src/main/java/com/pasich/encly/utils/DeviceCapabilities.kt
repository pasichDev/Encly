package com.pasich.encly.utils

import android.content.Context
import android.os.Build
import com.pasich.encly.core.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@Inject
class DeviceCapabilities(@param:ApplicationContext private val context: Context) {

    fun supportsDynamicColors(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}
