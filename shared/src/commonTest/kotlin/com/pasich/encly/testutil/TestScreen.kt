package com.pasich.encly.testutil

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.pasich.encly.core.security.BiometricHost
import com.pasich.encly.platform.ScreenBiometricHost

/**
 * A screen a biometric prompt is shown on, for ViewModels that tie a system prompt to the screen showing it: a
 * lifecycle (resumed unless [initial] says otherwise) and a host of its own, so calls made for one screen are
 * told from another's. [stop] and [resume] move it to and from the background; [destroy] ends it, as a rotation
 * does.
 */
internal class TestScreen(initial: Lifecycle.State = Lifecycle.State.RESUMED) {
    /** What the security core is given. */
    val biometric: BiometricHost = TestBiometricHost(nextId++)

    private val owner = object : LifecycleOwner {
        override val lifecycle: LifecycleRegistry = LifecycleRegistry.createUnsafe(this).apply {
            currentState = initial
        }
    }

    /** What the ViewModels are given. */
    val host = ScreenBiometricHost(biometric, owner)

    /** The app leaves the foreground: the activity is stopped. */
    fun stop() {
        owner.lifecycle.currentState = Lifecycle.State.CREATED
    }

    fun resume() {
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        owner.lifecycle.currentState = Lifecycle.State.DESTROYED
    }

    private data class TestBiometricHost(val id: Int) : BiometricHost

    private companion object {
        var nextId = 0
    }
}
