package com.pasich.encly.core.security

import androidx.fragment.app.FragmentActivity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The enrolment's copy of the DEK: gone on every way out of the prompt, also when its answer never comes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PendingDekTest {
    private fun key() = ByteArray(32) { 9 }

    @Test
    fun theCopyIsUsableUntilItIsWiped() {
        val source = key()
        val pending = PendingDek(source)

        assertArrayEquals(key(), pending.use { it.copyOf() })
        pending.wipe()

        assertNull(pending.use { it.copyOf() })
    }

    @Test
    fun theCopyIsIndependentOfTheCallersArray() {
        val source = key()
        val pending = PendingDek(source)
        SensitiveDataCleaner.clear(source)

        assertArrayEquals(key(), pending.use { it.copyOf() })
    }

    @Test
    fun wipingClearsTheBytesThemselves() {
        var held: ByteArray? = null
        val pending = PendingDek(key())
        pending.use { held = it }

        pending.wipe()

        assertArrayEquals(ByteArray(32), held)
    }

    @Test
    fun anActivityThatStopsBeforeTheAnswerWipesTheCopy() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).create().start().resume()
        val pending = PendingDek(key())
        var held: ByteArray? = null
        pending.use { held = it }
        pending.wipeWhenStopped(controller.get().lifecycle)

        controller.pause()
        assertArrayEquals("a paused activity may still get the answer", key(), pending.use { it.copyOf() })
        controller.stop()

        assertNull(pending.use { it.copyOf() })
        assertArrayEquals(ByteArray(32), held)
    }

    @Test
    fun anActivityDestroyedBeforeTheAnswerWipesTheCopy() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).create()
        val pending = PendingDek(key())
        pending.wipeWhenStopped(controller.get().lifecycle)

        controller.destroy()

        assertNull(pending.use { it.copyOf() })
    }

    @Test
    fun theAnswerWipesTheCopyAndStopsWatchingTheActivity() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).create().start().resume()
        val lifecycle = controller.get().lifecycle
        val before = lifecycle.observerCount
        val pending = PendingDek(key())
        pending.wipeWhenStopped(lifecycle)
        assertEquals(before + 1, lifecycle.observerCount)

        pending.wipe()

        assertEquals(before, lifecycle.observerCount)
        assertNull(pending.use { it.copyOf() })
    }

    private val androidx.lifecycle.Lifecycle.observerCount: Int
        get() = (this as androidx.lifecycle.LifecycleRegistry).observerCount
}
