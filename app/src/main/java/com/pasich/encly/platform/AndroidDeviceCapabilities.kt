package com.pasich.encly.platform

import android.os.Build
import com.pasich.encly.utils.DeviceCapabilities
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** [DeviceCapabilities] of an Android device. */
@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class)
class AndroidDeviceCapabilities : DeviceCapabilities {
    override fun supportsDynamicColors(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}
