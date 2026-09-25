package com.pasich.encly.testutil

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * A mocked FragmentActivity with a real, resumed lifecycle, for ViewModels that tie a system
 * prompt to the activity showing it. [destroy] ends it, as a rotation does.
 */
internal class MockActivity {
    val activity: FragmentActivity = mock(FragmentActivity::class.java)

    private val lifecycle = LifecycleRegistry.createUnsafe(activity).apply {
        currentState = Lifecycle.State.RESUMED
    }

    init {
        `when`(activity.lifecycle).thenReturn(lifecycle)
    }

    fun destroy() {
        lifecycle.currentState = Lifecycle.State.DESTROYED
    }
}
