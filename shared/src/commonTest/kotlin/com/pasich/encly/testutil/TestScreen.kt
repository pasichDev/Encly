package com.pasich.encly.testutil

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.pasich.encly.core.security.BiometricHost
import com.pasich.encly.platform.ScreenBiometricHost

/**
 * A screen a biometric prompt is shown on, for ViewModels that tie a system prompt to the screen showing it: a
 * resumed lifecycle and a host of its own, so calls made for one screen are told from another's. [destroy]
 * ends it, as a rotation does.
 */
internal class TestScreen {
    /** What the security core is given. */
    val biometric: BiometricHost = TestBiometricHost(nextId++)

    private val owner = object : LifecycleOwner {
        override val lifecycle: LifecycleRegistry = LifecycleRegistry.createUnsafe(this).apply {
            currentState = Lifecycle.State.RESUMED
        }
    }

    /** What the ViewModels are given. */
    val host = ScreenBiometricHost(biometric, owner)

    fun destroy() {
        owner.lifecycle.currentState = Lifecycle.State.DESTROYED
    }

    private data class TestBiometricHost(val id: Int) : BiometricHost

    private companion object {
        var nextId = 0
    }
}
