package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/**
 * At most one biometric prompt at a time, for a ViewModel that outlives its activity.
 *
 * A second request while a prompt is open is ignored: a double tap must not stack prompts. The
 * guard is released by the prompt's answer, or when the activity that showed it stops.
 * androidx.biometric 1.1 does not answer a prompt that cannot be shown or is cut off: its
 * authenticate() only logs "Called after onSaveInstanceState()" and returns, with no callback,
 * when the activity is already stopped; below Android 10 its fragment cancels the prompt
 * internally on ON_STOP without telling the caller; and it drops the answer of a prompt whose
 * activity is destroyed (BiometricPrompt resets its callback on ON_DESTROY). Without the release,
 * a prompt started over a backgrounded window (the auto-lock firing in the background) or cut
 * off by the app leaving the foreground would leave the guard closed for good: the lock screen's
 * fingerprint button, in a ViewModel that survives all of it, could never start a prompt again.
 *
 * Main thread only, as the prompts themselves.
 */
internal class BiometricPromptGuard {
    private var current: Any? = null

    /**
     * The prompt whose activity stopped while it was open. Its answer is still taken when no newer prompt
     * started since: a prompt that some devices show over a stopped activity answers after the stop.
     */
    private var stopped: Any? = null

    val inFlight: Boolean get() = current != null

    /**
     * Runs [prompt] unless one is already open. [prompt] calls the `release` it is given with
     * its answer; `release` returns false when that answer is stale (its activity was destroyed,
     * or a newer prompt started after its activity stopped), and the caller then drops it.
     */
    fun launch(host: LifecycleOwner, prompt: (release: () -> Boolean) -> Unit) {
        // A stopped or destroyed activity shows no prompt (it would be cancelled unseen) and
        // would not release the guard.
        if (current != null || !host.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        val token = Any()
        current = token
        stopped = null
        val observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // The button may start a new prompt now; this one's answer counts until then.
                if (current === token) {
                    current = null
                    stopped = token
                }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                owner.lifecycle.removeObserver(this)
                if (current === token) current = null
                if (stopped === token) stopped = null
            }
        }
        host.lifecycle.addObserver(observer)
        prompt {
            host.lifecycle.removeObserver(observer)
            val mine = current === token || stopped === token
            if (current === token) current = null
            if (stopped === token) stopped = null
            mine
        }
    }
}
