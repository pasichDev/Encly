package com.pasich.encly.core.security

import androidx.fragment.app.FragmentActivity

/**
 * The Android [BiometricHost]: the FragmentActivity BiometricPrompt attaches to. A data class so
 * that two hosts of the same Activity are equal (a screen that asks again after a recreation is
 * recognisable by its Activity).
 */
data class ActivityBiometricHost(val activity: FragmentActivity) : BiometricHost

/** The Activity behind a host this app made, or null for any other. */
internal fun BiometricHost.activityOrNull(): FragmentActivity? = (this as? ActivityBiometricHost)?.activity
