package com.pasich.encly.utils

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceCapabilities @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    fun supportsDynamicColors(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }


}
