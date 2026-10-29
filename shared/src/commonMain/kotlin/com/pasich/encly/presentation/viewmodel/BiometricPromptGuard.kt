package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/**
 * At most one biometric prompt at a time, for a ViewModel that outlives its activity.
 *
 * A second request while a prompt is open is ignored: a double tap must not stack prompts. The
 * guard is released by the prompt's answer, or when the activity that showed it is destroyed.
 * androidx.biometric drops the answer of a prompt whose activity is gone (BiometricPrompt resets
 * its callback on ON_DESTROY), so without that a rotation during a prompt would leave the guard
 * closed for good and the ViewModel, which survives the rotation, could never prompt again.
 *
 * Main thread only, as the prompts themselves.
 */
internal class BiometricPromptGuard {
    private var current: Any? = null

    val inFlight: Boolean get() = current != null

    /**
     * Runs [prompt] unless one is already open. [prompt] calls the `release` it is given with
     * its answer; `release` returns false when that answer is stale (the guard was released by
     * the activity's end meanwhile, and a newer prompt may be open), and the caller then drops it.
     */
    fun launch(host: LifecycleOwner, prompt: (release: () -> Boolean) -> Unit) {
        // A destroyed activity can show no prompt, and would never release the guard.
        if (current != null || host.lifecycle.currentState == Lifecycle.State.DESTROYED) return
        val token = Any()
        current = token
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                owner.lifecycle.removeObserver(this)
                if (current === token) current = null
            }
        }
        host.lifecycle.addObserver(observer)
        prompt {
            host.lifecycle.removeObserver(observer)
            val mine = current === token
            if (mine) current = null
            mine
        }
    }
}
