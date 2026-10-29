package com.pasich.encly.utils

/** What the device the app runs on can do that the UI offers or hides accordingly. */
interface DeviceCapabilities {
    /** True where the system can colour the app from the user's wallpaper (Android 12 and later). */
    fun supportsDynamicColors(): Boolean
}
