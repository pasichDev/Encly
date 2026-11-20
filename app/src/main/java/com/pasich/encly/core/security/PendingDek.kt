package com.pasich.encly.core.security

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * The copy of the DEK an enrolment holds while its prompt is open. It is wiped on every way out: the prompt's
 * answer, a failure, and an activity that is destroyed, because androidx.biometric drops the answer of a
 * prompt whose activity is gone and then nothing else would ever clear it. A stop does not wipe it: some
 * devices show the prompt over a stopped activity and answer after the stop. A recreation for a configuration
 * change does not wipe it either: the prompt survives it and answers the same callback. A late answer finds it wiped
 * ([use] gives null) and enrols nothing.
 *
 * Main thread only, as the prompt's callbacks and the lifecycle.
 */
internal class PendingDek(source: ByteArray) {
    private var dek: ByteArray? = source.copyOf()
    private var detach: (() -> Unit)? = null

    /** Runs [block] on the key, or gives null when it was wiped already. */
    fun <T> use(block: (ByteArray) -> T): T? = dek?.let(block)

    /** Wipes the copy, and stops watching the lifecycle. */
    fun wipe() {
        dek?.let(SensitiveDataCleaner::clear)
        dek = null
        detach?.invoke()
        detach = null
    }

    /** Wipes the copy when [lifecycle] reaches ON_DESTROY. */
    fun wipeWhenDestroyed(lifecycle: Lifecycle) {
        if (dek == null) return
        val observer = LifecycleEventObserver { owner, event ->
            if (event != Lifecycle.Event.ON_DESTROY) return@LifecycleEventObserver
            // A recreation keeps the prompt and answers its callback: the copy lives on, only this watch ends.
            if ((owner as? Activity)?.isChangingConfigurations == true) {
                detach?.invoke()
                detach = null
            } else {
                wipe()
            }
        }
        lifecycle.addObserver(observer)
        detach = { lifecycle.removeObserver(observer) }
    }
}
